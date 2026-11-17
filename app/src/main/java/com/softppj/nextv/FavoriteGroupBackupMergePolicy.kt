package com.softppj.nextv

import org.json.JSONArray
import org.json.JSONObject

/** Merge named favourite groups without replacing existing curated membership. */
internal object FavoriteGroupBackupMergePolicy {
    fun merge(existing:String,incoming:String):String {
        val current=JSONObject(existing)
        val imported=JSONObject(incoming)
        val names=imported.keys()
        while(names.hasNext()){
            val name=names.next()
            val merged=linkedSetOf<String>()
            val oldMembers=current.optJSONArray(name)
            if(oldMembers!=null)for(i in 0 until oldMembers.length())
                oldMembers.optString(i).takeIf{it.isNotBlank()}?.let(merged::add)
            val newMembers=imported.getJSONArray(name)
            for(i in 0 until newMembers.length())
                newMembers.optString(i).takeIf{it.isNotBlank()}?.let(merged::add)
            current.put(name,JSONArray(merged.toList()))
        }
        return current.toString()
    }
}
