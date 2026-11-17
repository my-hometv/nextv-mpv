package com.softppj.nextv

import android.util.JsonReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional public *logo-only* directory; no playlist URLs, channel credentials,
 * license information or user identifiers are sent to this service.
 */
internal object ExternalLogoCatalog {
    const val DIRECTORY = "https://iptv-org.github.io/api/logos.json"
    private const val MAX_RESPONSE_BYTES = 20 * 1024 * 1024
    private const val MAX_LOGO_RECORDS = 150_000
    const val REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L

    fun fetch(targets:List<ExternalLogoMatchPolicy.Target>):Map<String,String> {
        if (targets.isEmpty()) return emptyMap()
        val connection=(URL(DIRECTORY).openConnection() as HttpURLConnection).apply {
            connectTimeout=6000
            readTimeout=12000
            instanceFollowRedirects=false
            setRequestProperty("Accept","application/json")
        }
        try {
            if (connection.responseCode != 200) return emptyMap()
            if (connection.contentLengthLong > MAX_RESPONSE_BYTES) return emptyMap()
            connection.inputStream.use { source ->
                val bounded=object:FilterInputStream(source) {
                    var consumed=0
                    override fun read():Int {
                        if(consumed>=MAX_RESPONSE_BYTES)throw java.io.IOException("Logo catalogue too large")
                        val result=super.read()
                        if(result>=0)consumed++
                        return result
                    }
                    override fun read(buffer:ByteArray,offset:Int,length:Int):Int {
                        if(consumed>=MAX_RESPONSE_BYTES)throw java.io.IOException("Logo catalogue too large")
                        val result=super.read(buffer,offset,minOf(length,MAX_RESPONSE_BYTES-consumed))
                        if(result>0)consumed+=result
                        return result
                    }
                }
                val matched=ArrayList<ExternalLogoMatchPolicy.Entry>()
                JsonReader(InputStreamReader(bounded,Charsets.UTF_8)).use { json ->
                    json.beginArray()
                    var count=0
                    while(json.hasNext()){
                        if(++count>MAX_LOGO_RECORDS)throw java.io.IOException("Logo catalogue entry limit")
                        var channel=""
                        var url=""
                        var format=""
                        var inUse=false
                        json.beginObject()
                        while(json.hasNext()){
                            when(json.nextName()){
                                "channel"->channel=if(json.peek()==android.util.JsonToken.NULL){json.nextNull();""}else json.nextString()
                                "url"->url=if(json.peek()==android.util.JsonToken.NULL){json.nextNull();""}else json.nextString()
                                "format"->format=if(json.peek()==android.util.JsonToken.NULL){json.nextNull();""}else json.nextString()
                                "in_use"->inUse=if(json.peek()==android.util.JsonToken.NULL){json.nextNull();false}else json.nextBoolean()
                                else->json.skipValue()
                            }
                        }
                        json.endObject()
                        val entry=ExternalLogoMatchPolicy.Entry(channel,url,format,inUse)
                        if(ExternalLogoMatchPolicy.matchesAny(entry,targets))matched.add(entry)
                    }
                    json.endArray()
                }
                return ExternalLogoMatchPolicy.build(matched)
            }
        }finally{
            connection.disconnect()
        }
    }
}
