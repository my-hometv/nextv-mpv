package com.softppj.nextv
import org.json.JSONObject
internal object M3uParser {
 /**
  * M3U header metadata is independent of the per-channel #EXTINF records.
  * Extract XMLTV sources from the FIRST #EXTM3U header only. Never interpret
  * a stream URL or a channel attribute as an EPG configuration.
  */
 fun headerEpgUrls(body:String):List<String>{
  val header=body.lineSequence().firstOrNull{it.isNotBlank()}?.trim()
   ?.removePrefix("\uFEFF")?.trim() ?:return emptyList()
  if(!header.startsWith("#EXTM3U",ignoreCase=true))return emptyList()
  val attrs=Regex("""(?:^|\\s)(?:url-tvg|x-tvg-url|tvg-url)\\s*=\\s*["']([^"']*)["']""",RegexOption.IGNORE_CASE)
  return attrs.findAll(header).flatMap{match->
   match.groupValues[1].split(',',';').asSequence()
  }.map{it.trim()}.filter{url->
   url.length in 10..2048 && url.none{it.code<32 || it.code==127} &&
    (url.startsWith("https://",true)||url.startsWith("http://",true))
  }.distinct().take(8).toList()
 }
 fun parse(body:String,source:String,deletedGroups:Set<String> = emptySet()):MutableList<Channel>{
  val out=mutableListOf<Channel>()
  var name="";var group="Other";var logo="";var epgId="";var webChannel=""
  var mediaType="live";var year="";var genre="";var artist="";var album="";var season="";var episode="";var description=""
  var headers=mutableMapOf<String,String>()
  val attributes=listOf("group-title","tvg-logo","tvg-id","web-channel","user-agent","referrer","media-type","year","genre","artist","album","season","episode","series-name","description")
   .associateWith{key->Regex(Regex.escape(key)+"""="([^"]*)" """.trim())}
  fun attr(line:String,key:String)=attributes[key]?.find(line)?.groupValues?.get(1).orEmpty()
  fun applyHeader(key:String,value:String){
   val normalized=when(key.lowercase()){
    "http-user-agent","user-agent"->"User-Agent"
    "http-referrer","http-referer","referer","referrer"->"Referer"
    "http-origin","origin"->"Origin"
    else->return
   }
   // Reject CR/LF and control characters: playlist-provided headers must
   // never be able to inject additional HTTP request header lines.
   val valid=value.none { it.code < 32 || it.code == 127 }
   val clean=value.trim()
   if(valid && clean.isNotEmpty())
    headers[normalized]=clean
  }
  body.lineSequence().forEach{raw->
   val line=raw.trim()
   when{
    line.startsWith("#EXTINF",true)->{
     name=line.substringAfter(",","").trim();group=attr(line,"group-title").ifBlank{"Other"}
     logo=attr(line,"tvg-logo");epgId=attr(line,"tvg-id");webChannel=attr(line,"web-channel")
     mediaType=attr(line,"media-type").trim().lowercase(java.util.Locale.ROOT)
      .ifBlank{if(webChannel.isNotBlank() && !webChannel.equals("no",true))"web" else "live"}
     if(mediaType !in setOf("live","movie","series","music","web"))mediaType="live"
     year=attr(line,"year");genre=attr(line,"genre");artist=attr(line,"artist");album=attr(line,"album")
     season=attr(line,"season");episode=attr(line,"episode");description=attr(line,"description")
     if(mediaType=="series" && attr(line,"series-name").isNotBlank())group=attr(line,"series-name")
     headers=mutableMapOf();applyHeader("user-agent",attr(line,"user-agent"));applyHeader("referer",attr(line,"referrer"))
    }
    name.isNotEmpty() && line.startsWith("#EXTVLCOPT:",true)->{
     val pair=line.substringAfter(":").split("=",limit=2)
     if(pair.size==2)applyHeader(pair[0],pair[1])
    }
    name.isNotEmpty() && line.startsWith("#EXTHTTP:",true)->{
     try{val obj=JSONObject(line.substringAfter(":").trim());obj.keys().forEach{key->applyHeader(key,obj.optString(key))}}catch(_:Exception){}
    }
    line.isNotEmpty() && !line.startsWith("#") && name.isNotEmpty()->{
     val parts=line.split("|",limit=2);val url=parts[0].trim();val localHeaders=headers.toMutableMap()
     if(parts.size==2)parts[1].split("&").forEach{kv->
      val pair=kv.split("=",limit=2)
      if(pair.size==2){
       val normalized=when(pair[0].lowercase()){
        "user-agent","http-user-agent"->"User-Agent"
        "referer","referrer","http-referrer"->"Referer"
        "origin"->"Origin"
        else->null
       }
       if(normalized!=null){
        val decoded=try{java.net.URLDecoder.decode(pair[1],"UTF-8")}catch(_:Exception){pair[1]}
        val valid=decoded.none { it.code < 32 || it.code == 127 }
        val clean=decoded.trim()
        if(valid && clean.isNotEmpty())
         localHeaders[normalized]=clean
       }
      }
     }
     val scheme=url.substringBefore("://","").lowercase(java.util.Locale.ROOT)
     if("://" in url && scheme in setOf("http","https","rtmp","rtmps","rtsp","rtp","udp","mmsh","mms") && source+"|"+group !in deletedGroups)
      out.add(Channel(name,url,group,logo,source,localHeaders,epgId,webChannel,mediaType,year,genre,artist,album,season,episode,description))
     // Multiple stream URLs may follow one EXTINF (fallback URLs).
     // Their metadata remains scoped to this EXTINF and is reset when
     // the next EXTINF begins.
    }
   }
  }
  return out
 }
}
