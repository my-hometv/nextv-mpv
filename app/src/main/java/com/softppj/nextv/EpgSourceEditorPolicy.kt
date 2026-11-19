package com.softppj.nextv

/** Manage user-entered XMLTV sources without changing playlist-announced feeds. */
internal object EpgSourceEditorPolicy {
    const val MAX_MANUAL_SOURCES=16

    fun read(stored:String?):List<String> = stored.orEmpty().lineSequence()
        .map{it.trim()}.filter{it.isNotEmpty()}.distinct().toList()

    fun valid(url:String):Boolean {
        val clean=url.trim()
        if(clean.length !in 10..2048 || clean.any{it.isWhitespace() || it.code<32 || it.code==127})
            return false
        val uri=try{java.net.URI(clean)}catch(_:Exception){return false}
        return (uri.scheme.equals("http",true) || uri.scheme.equals("https",true)) &&
            !uri.host.isNullOrBlank() && uri.userInfo==null
    }

    fun add(existing:List<String>,url:String):List<String>? {
        val clean=url.trim()
        if(!valid(clean) || clean in existing || existing.size>=MAX_MANUAL_SOURCES)return null
        return existing+clean
    }

    fun edit(existing:List<String>,index:Int,url:String):List<String>? {
        if(index !in existing.indices)return null
        val clean=url.trim()
        if(!valid(clean) || existing.withIndex().any{it.index!=index && it.value==clean})
            return null
        return existing.toMutableList().apply{set(index,clean)}
    }

    fun move(existing:List<String>,index:Int,direction:Int):List<String>? {
        val target=index+direction
        if(index !in existing.indices || target !in existing.indices)return null
        return existing.toMutableList().apply{
            val value=removeAt(index)
            add(target,value)
        }
    }

    fun remove(existing:List<String>,index:Int):List<String>? {
        if(index !in existing.indices)return null
        return existing.toMutableList().apply{removeAt(index)}
    }
}
