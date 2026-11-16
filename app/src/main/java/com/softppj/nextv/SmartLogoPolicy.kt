package com.softppj.nextv

/** Reuses a logo from another entry for the same recognized channel, offline. */
internal object SmartLogoPolicy {
    data class Entry(val name:String,val epgId:String,val logo:String)
    private fun nameKey(name:String):String=name.trim().lowercase(java.util.Locale.ROOT)
        .replace(Regex("""\s*[\[(](?:hd|fhd|uhd|4k|sd)[\])]\s*$"""),"")
        .replace(Regex("""\s+(?:hd|fhd|uhd|4k|sd)$"""),"")
        .replace(Regex("""\s+""")," ").trim()
    private fun idKey(id:String)=id.trim().lowercase(java.util.Locale.ROOT)
    private fun valid(url:String)=url.startsWith("https://",true) || url.startsWith("http://",true)

    fun build(entries:List<Entry>):Map<String,String> {
        val seen=mutableMapOf<String,MutableSet<String>>()
        for(item in entries){
            val logo=item.logo.trim()
            if(!valid(logo) || logo.length>2048)continue
            val id=idKey(item.epgId)
            val name=nameKey(item.name)
            if(id.isNotBlank())seen.getOrPut("id:"+id){mutableSetOf()}.add(logo)
            if(name.isNotBlank())seen.getOrPut("name:"+name){mutableSetOf()}.add(logo)
        }
        // Do not guess when similarly named channels advertise different artwork.
        return seen.mapNotNull{(key,urls)->if(urls.size==1)key to urls.single() else null}.toMap()
    }

    fun resolve(name:String,epgId:String,index:Map<String,String>):String? {
        val id=idKey(epgId)
        if(id.isNotBlank())index["id:"+id]?.let{return it}
        val key=nameKey(name)
        return if(key.isBlank())null else index["name:"+key]
    }
}
