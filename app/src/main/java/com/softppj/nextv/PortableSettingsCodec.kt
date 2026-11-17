package com.softppj.nextv

import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared codec for portable Settings export and restore. Keeping the two
 * directions here lets unit tests exercise the exact production wire format.
 * Device activation, PIN and other forbidden keys are never transferred.
 */
internal object PortableSettingsCodec {
    private const val FORMAT="HomeTV-settings-v1"

    fun encode(settings:Map<String,*>):String {
        val data=JSONObject()
        settings.forEach{(key,value)->
            if(!BackupSafety.mayTransferSetting(key))return@forEach
            val item=when(value){
                is String->JSONObject().put("type","string").put("value",value)
                is Boolean->JSONObject().put("type","boolean").put("value",value)
                is Int->JSONObject().put("type","int").put("value",value)
                is Long->JSONObject().put("type","long").put("value",value)
                is Float->JSONObject().put("type","float").put("value",value.toDouble())
                is Set<*>->JSONObject().put("type","strings")
                    .put("value",JSONArray(value.filterIsInstance<String>()))
                else->null
            }
            if(item!=null)data.put(key,item)
        }
        val raw=JSONObject().put("format",FORMAT).put("settings",data).toString(2)
        require(BackupSafety.fitsBackupLimit(raw)){"Settings backup exceeds the 2 MB portable limit"}
        return raw
    }

    fun decode(raw:String):List<Pair<String,Any>> {
        require(BackupSafety.fitsBackupLimit(raw)){"Backup exceeds the 2 MB safety limit"}
        val backup=JSONObject(raw)
        require(backup.optString("format")==FORMAT){"Unsupported backup format"}
        val values=backup.getJSONObject("settings")
        val staged=mutableListOf<Pair<String,Any>>()
        val iterator=values.keys()
        while(iterator.hasNext()){
            val key=iterator.next()
            if(!BackupSafety.mayTransferSetting(key))continue
            val item=values.getJSONObject(key)
            val value:Any=when(item.getString("type")){
                "string"->item.getString("value")
                "boolean"->item.getBoolean("value")
                "int"->item.getInt("value")
                "long"->item.getLong("value")
                "float"->item.getDouble("value").toFloat()
                "strings"->{
                    val arr=item.getJSONArray("value")
                    (0 until arr.length()).map{arr.getString(it)}.toSet()
                }
                else->throw IllegalArgumentException("Unsupported setting type for "+key)
            }
            staged.add(key to value)
        }
        return staged
    }
}
