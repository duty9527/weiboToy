package com.duty.weibotoy.data

// Comment Models
data class WeiboCommentBadge(
    val pic_url: String? = null,
    val name: String? = null,
    val length: Double? = null,
    val scheme: String? = null
)

data class WeiboCommentDynamicMessage(
    val id: String? = null,
    val icon_url: String? = null,
    val tag_text: String? = null
)

data class WeiboComment(
    val id: Long?,
    val text: String?,
    val created_at: String?,
    val like_counts: Int? = null,
    val like_count: Int? = null,
    val like_num: Int? = null,
    val user: WeiboTimelineUser? = null,
    @com.google.gson.annotations.SerializedName(value = "comments", alternate = ["comment"])
    val comments: List<WeiboComment>? = null,
    val total_number: Int? = null,
    val pic: WeiboPic? = null,
    val is_mblog_author: Boolean? = null,
    val floor_number: Int? = null,
    val source: String? = null,
    val comment_badge: List<WeiboCommentBadge>? = null,
    val comment_dynamic_message: WeiboCommentDynamicMessage? = null
)

data class WeiboCommentsData(
    val data: List<WeiboComment>?,
    val max_id: Long?,
    val max_id_type: Int?,
    val total_number: Int?
)

data class WeiboCommentsResponse(
    val ok: Int?,
    val data: WeiboCommentsData?,
    val errno: String? = null,
    val msg: String? = null,
    val url: String? = null
)

data class WeiboCommentChildrenResponse(
    val ok: Int?,
    val data: List<WeiboComment>?,
    val total_number: Int?,
    val max_id: Long?,
    val max_id_type: Int?
)

// Repost Models
data class WeiboRepost(
    val id: Long?,
    val text: String?,
    val created_at: String?,
    val like_counts: Int?,
    val user: WeiboTimelineUser?
)

data class WeiboRepostsData(
    val data: List<WeiboRepost>?,
    val total_number: Int?
)

data class WeiboRepostsResponse(
    val ok: Int?,
    val data: WeiboRepostsData?
)

// Attitude (Like) Models
data class WeiboAttitude(
    val id: Long?,
    val created_at: String?,
    val attitude: String?,
    val user: WeiboTimelineUser?
)

data class WeiboAttitudesData(
    val data: List<WeiboAttitude>?,
    val total_number: Int?
)

data class WeiboAttitudesResponse(
    val ok: Int?,
    val data: WeiboAttitudesData?
)

// Common Response
data class WeiboCommonResponse(
    val ok: Int?,
    val msg: String?
)

// TypeAdapterFactory for WeiboComment list deserialization
class WeiboCommentListTypeAdapterFactory : com.google.gson.TypeAdapterFactory {
    override fun <T : Any?> create(gson: com.google.gson.Gson, typeToken: com.google.gson.reflect.TypeToken<T>): com.google.gson.TypeAdapter<T>? {
        val rawType = typeToken.rawType
        if (List::class.java.isAssignableFrom(rawType)) {
            val type = typeToken.type
            if (type is java.lang.reflect.ParameterizedType) {
                val args = type.actualTypeArguments
                if (args.size == 1) {
                    val arg = args[0]
                    val isWeiboComment = if (arg is Class<*>) {
                        WeiboComment::class.java.isAssignableFrom(arg)
                    } else if (arg is java.lang.reflect.WildcardType) {
                        val upperBounds = arg.upperBounds
                        upperBounds.size == 1 && WeiboComment::class.java.isAssignableFrom(upperBounds[0] as Class<*>)
                    } else {
                        false
                    }
                    if (isWeiboComment) {
                        val elementAdapter = gson.getAdapter(WeiboComment::class.java)
                        @Suppress("UNCHECKED_CAST")
                        return object : com.google.gson.TypeAdapter<List<WeiboComment>>() {
                            override fun write(out: com.google.gson.stream.JsonWriter, value: List<WeiboComment>?) {
                                if (value == null) {
                                    out.nullValue()
                                    return
                                }
                                out.beginArray()
                                for (item in value) {
                                    elementAdapter.write(out, item)
                                }
                                out.endArray()
                            }

                            override fun read(reader: com.google.gson.stream.JsonReader): List<WeiboComment>? {
                                val token = reader.peek()
                                if (token == com.google.gson.stream.JsonToken.BOOLEAN) {
                                    reader.nextBoolean() // consume boolean
                                    return null
                                }
                                if (token == com.google.gson.stream.JsonToken.NULL) {
                                    reader.nextNull()
                                    return null
                                }
                                if (token == com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                                    reader.beginArray()
                                    val list = mutableListOf<WeiboComment>()
                                    while (reader.hasNext()) {
                                        val item = elementAdapter.read(reader)
                                        if (item != null) {
                                            list.add(item)
                                        }
                                    }
                                    reader.endArray()
                                    return list
                                }
                                reader.skipValue()
                                return null
                            }
                        } as com.google.gson.TypeAdapter<T>
                    }
                }
            }
        }
        return null
    }
}
