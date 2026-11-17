package com.softppj.nextv

import org.json.JSONArray
import org.json.JSONObject

/**
 * Merge playlist entries by their exact stream URL. IPTV URL paths, tokens,
 * Xtream credentials and query values may be case-sensitive.
 */
internal object PlaylistBackupMergePolicy {
    fun merge(existing:String,incoming:String):String {
        val merged=linkedMapOf<String,JSONObject>()
        fun add(raw:String){
            val array=JSONArray(raw)
            for(i in 0 until array.length()){
                val item=array.optJSONObject(i) ?:continue
                val url=item.optString("url").trim()
                if(url.isBlank())continue
                // Existing entries keep order, incoming exact matches replace
                // their settings without merging distinct case-sensitive URLs.
                merged[url]=JSONObject(item.toString())
            }
        }
        add(existing)
        add(incoming)
        return JSONArray().apply{merged.values.forEach{put(it)}}.toString()
    }
}
