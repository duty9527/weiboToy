package com.duty.weibotoy.data

import junit.framework.TestCase.assertEquals
import org.junit.Test
import com.duty.weibotoy.ui.weibo.components.parseWeiboHtmlText
import com.duty.weibotoy.ui.weibo.components.parseWeiboRepostChain
import com.duty.weibotoy.ui.weibo.components.parseMarkdownBlocks
import com.duty.weibotoy.ui.weibo.components.MarkdownBlock
import com.duty.weibotoy.ui.weibo.components.TableAlign
import com.duty.weibotoy.ui.weibo.components.WeiboRepostChainItem

class MessageTextCleanerTest {
    @Test
    fun cleanWeiboHtmlText_keepsEmojiAltAndReadableText() {
        val raw = """
            对着手机喊小爱同学控制确实方便很多<br />
            <a href='https://m.weibo.cn/n/我爱吃肠粉001'>@我爱吃肠粉001</a>
            <span class="url-icon"><img alt="[笑cry]" src="https://face.t.sinajs.cn/t4/appstyle/expression/ext/normal/f1/201810_xiaoku_mobile.png" style="width:1em; height:1em;" /></span>
        """.trimIndent()

        assertEquals(
            "对着手机喊小爱同学控制确实方便很多\n@我爱吃肠粉001\n[笑cry]",
            cleanWeiboHtmlText(raw)
        )
    }

    @Test
    fun cleanWeiboHtmlText_decodesEscapedLineBreaks() {
        assertEquals(
            "第一行\n第二行",
            cleanWeiboHtmlText("第一行&#10;第二行")
        )
    }

    @Test
    fun parseWeiboHtmlText_extractsEmojisAndLinksCorrectly() {
        val raw = "Hello <span class=\"url-icon\"><img alt=\"[笑cry]\" src=\"https://face.t.sinajs.cn/emoji.png\" /></span> world!"
        val parsed = parseWeiboHtmlText(raw)
        // Check that the text length is correct and emoji is mapped
        assertEquals("Hello \uFFFC world!", parsed.annotatedString.text)
        assertEquals(1, parsed.inlineContent.size)
        val emojiKey = parsed.inlineContent.keys.first()
        assertEquals("emoji_0", emojiKey)
    }

    @Test
    fun parseWeiboHtmlText_stripsLocationAndTopicLinks() {
        // Location link: stripped
        val rawLoc = "逛街中 <a href='https://m.weibo.cn/p/index?containerid=location_123'>北京三里屯</a> 和好朋友一起"
        val parsedLoc = parseWeiboHtmlText(rawLoc)
        assertEquals("逛街中  和好朋友一起", parsedLoc.annotatedString.text)

        // Topic link: kept
        val rawTopic = "关于 <a href='https://m.weibo.cn/search?q=%23iPhone18%23'>#iPhone18#</a> 消息"
        val parsedTopic = parseWeiboHtmlText(rawTopic)
        assertEquals("关于 #iPhone18# 消息", parsedTopic.annotatedString.text)
        assertEquals(1, parsedTopic.annotatedString.getStringAnnotations("URL", 0, parsedTopic.annotatedString.length).size)
    }

    @Test
    fun parseWeiboHtmlText_keepsCommentImageAndMediaLinks() {
        // Without icon
        val raw = "真好看呀 <a href='https://m.weibo.cn/detail/123'>评论配图</a> 哈哈哈"
        val parsed = parseWeiboHtmlText(raw)
        assertEquals("真好看呀 评论配图 哈哈哈", parsed.annotatedString.text)
        assertEquals(1, parsed.annotatedString.getStringAnnotations("URL", 0, parsed.annotatedString.length).size)

        // With icon
        val rawWithIcon = "点击这里 <a href='https://weibo.com/foo'><span class='url-icon'><img src='https://h5.sinaimg.cn/icon.png'></span><span>网页链接</span></a>"
        val parsedWithIcon = parseWeiboHtmlText(rawWithIcon)
        assertEquals("点击这里 \uFFFD网页链接", parsedWithIcon.annotatedString.text)
        assertEquals(1, parsedWithIcon.inlineContent.size)
        assertEquals(1, parsedWithIcon.annotatedString.getStringAnnotations("URL", 0, parsedWithIcon.annotatedString.length).size)
    }

    @Test
    fun parseWeiboHtmlText_stripsLocationName() {
        // Case 1: Location name as a link text matching locationName
        val raw1 = "我在 <a href='http://s.weibo.com/weibo?q=上海'>上海</a> 玩得很开心"
        val parsed1 = parseWeiboHtmlText(raw1, locationName = "上海")
        assertEquals("我在  玩得很开心", parsed1.annotatedString.text)

        // Case 2: Location name as plain text suffix matching locationName
        val raw2 = "我在上海玩 📍 上海"
        val parsed2 = parseWeiboHtmlText(raw2, locationName = "上海")
        assertEquals("我在上海玩", parsed2.annotatedString.text)
    }

    @Test
    fun parseWeiboHtmlText_resolvesWeiboRedirectLinks() {
        val raw = "微信公众号： <a href='https://weibo.cn/sinaurl?u=https%3A%2F%2Fmp.weixin.qq.com%2Fs%2FlezKQPHoZSsKMVEehwCHcg'><span class='url-icon'><img src='https://h5.sinaimg.cn/icon.png'></span><span>网页链接</span></a>"
        val parsed = parseWeiboHtmlText(raw)
        assertEquals("微信公众号： \uFFFD网页链接", parsed.annotatedString.text)
        
        val urlAnnotations = parsed.annotatedString.getStringAnnotations("URL", 0, parsed.annotatedString.length)
        assertEquals(1, urlAnnotations.size)
        assertEquals("https://mp.weixin.qq.com/s/lezKQPHoZSsKMVEehwCHcg", urlAnnotations[0].item)
    }

    @Test
    fun parseMarkdownBlocks_correctlyClassifiesBlocks() {
        val raw = """
            # Header 1
            
            Some normal text here.
            
            > A blockquote line 1
            > A blockquote line 2
            
            - List item 1
            - List item 2
            
            ```kotlin
            fun test() {}
            ```
        """.trimIndent()
        
        val blocks = parseMarkdownBlocks(raw)
        
        assertEquals(5, blocks.size)
        
        // Block 1: Header
        val h1 = blocks[0] as MarkdownBlock.Header
        assertEquals(1, h1.level)
        assertEquals("Header 1", h1.text)
        
        // Block 2: Paragraph
        val p = blocks[1] as MarkdownBlock.Paragraph
        assertEquals("Some normal text here.", p.text)
        
        // Block 3: Blockquote
        val q = blocks[2] as MarkdownBlock.Blockquote
        assertEquals(2, q.lines.size)
        assertEquals("A blockquote line 1", q.lines[0])
        assertEquals("A blockquote line 2", q.lines[1])
        
        // Block 4: BulletList
        val list = blocks[3] as MarkdownBlock.BulletList
        assertEquals(2, list.items.size)
        assertEquals("List item 1", list.items[0])
        assertEquals("List item 2", list.items[1])
        
        // Block 5: CodeBlock
        val code = blocks[4] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("fun test() {}", code.content)
    }

    @Test
    fun parseWeiboHtmlText_rendersInlineMarkdownStylesCorrectly() {
        // Test inline code, bold, italic
        val raw = "This is `code`, **bold**, and *italic*."
        val parsed = parseWeiboHtmlText(raw, isMarkdown = true)
        
        // Formatting characters (`, **, *) are stripped in the resulting text
        assertEquals("This is code, bold, and italic.", parsed.annotatedString.text)
        
        // The bold segment "bold" should have bold style
        val boldAnnotations = parsed.annotatedString.spanStyles.filter { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold }
        assertEquals(1, boldAnnotations.size)
        assertEquals(14, boldAnnotations[0].start)
        assertEquals(18, boldAnnotations[0].end)
        
        // The italic segment "italic" should have italic style
        val italicAnnotations = parsed.annotatedString.spanStyles.filter { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic }
        assertEquals(1, italicAnnotations.size)
        assertEquals(24, italicAnnotations[0].start)
        assertEquals(30, italicAnnotations[0].end)
    }

    @Test
    fun parseWeiboHtmlText_keepsLinksAlignedWhenMarkdownStylesAreStripped() {
        val raw = "# Header\n\nSome **bold** link: <a href='https://weibo.com/test'>网页链接</a>"
        val parsed = parseWeiboHtmlText(raw, isMarkdown = true)
        
        // Raw text does not strip '#' because headers are parsed at block level
        assertEquals("# Header\n\nSome bold link: 网页链接", parsed.annotatedString.text)
        
        val urlAnnotations = parsed.annotatedString.getStringAnnotations("URL", 0, parsed.annotatedString.length)
        assertEquals(1, urlAnnotations.size)
        assertEquals("https://weibo.com/test", urlAnnotations[0].item)
        
        // Offset mapping check: "网页链接" should be annotated.
        // Index of "网页链接" in "# Header\n\nSome bold link: 网页链接":
        // "# Header\n\nSome bold link: " length is 26.
        // So start=26, end=30.
        assertEquals(26, urlAnnotations[0].start)
        assertEquals(30, urlAnnotations[0].end)
    }

    @Test
    fun parseMarkdownBlocks_correctlyParsesTables() {
        val raw = """
            | Header 1 | Header 2 | Header 3 |
            | :--- | :---: | ---: |
            | Cell 1 | Cell 2 | Cell 3 |
            | Cell 4 | Cell 5 | Cell 6 |
        """.trimIndent()
        
        val blocks = parseMarkdownBlocks(raw)
        
        assertEquals(1, blocks.size)
        val table = blocks[0] as MarkdownBlock.Table
        
        // Assert headers
        assertEquals(3, table.headers.size)
        assertEquals("Header 1", table.headers[0])
        assertEquals("Header 2", table.headers[1])
        assertEquals("Header 3", table.headers[2])
        
        // Assert alignments
        assertEquals(3, table.alignments.size)
        assertEquals(TableAlign.LEFT, table.alignments[0])
        assertEquals(TableAlign.CENTER, table.alignments[1])
        assertEquals(TableAlign.RIGHT, table.alignments[2])
        
        // Assert rows
        assertEquals(2, table.rows.size)
        assertEquals(3, table.rows[0].size)
        assertEquals("Cell 1", table.rows[0][0])
        assertEquals("Cell 2", table.rows[0][1])
        assertEquals("Cell 3", table.rows[0][2])
        
        assertEquals("Cell 4", table.rows[1][0])
        assertEquals("Cell 5", table.rows[1][1])
        assertEquals("Cell 6", table.rows[1][2])
    }

    @Test
    fun parseWeiboCommentsResponse_deserializesAllFieldsCorrectly() {
        val json = """
            {
              "ok": 1,
              "data": {
                "data": [
                  {
                    "created_at": "Wed May 27 10:36:04 +0800 2026",
                    "id": 5303134956294744,
                    "text": "饿死了饿死了",
                    "floor_number": 16,
                    "source": "来自上海",
                    "is_mblog_author": false,
                    "comment_badge": [
                      {
                        "pic_url": "https://h5.sinaimg.cn/badge.png",
                        "name": "loyal_fans",
                        "length": 1.33
                      }
                    ],
                    "comment_dynamic_message": {
                      "id": "0055",
                      "icon_url": "https://h5.sinaimg.cn/dynamic.png",
                      "tag_text": "年费"
                    },
                    "comments": [
                      {
                        "created_at": "Wed May 27 10:37:33 +0800 2026",
                        "id": 5303135330894775,
                        "text": "我已下单老乡鸡",
                        "is_mblog_author": true,
                        "source": "来自安徽"
                      }
                    ]
                  },
                  {
                    "created_at": "Wed May 27 10:35:56 +0800 2026",
                    "id": 5303134922475273,
                    "text": "上班迟到了是鸡窝潮湿吗",
                    "floor_number": 15,
                    "source": "来自浙江",
                    "is_mblog_author": false,
                    "comments": false
                  }
                ]
              }
            }
        """.trimIndent()

        val gson = com.google.gson.GsonBuilder()
            .registerTypeAdapterFactory(WeiboCommentListTypeAdapterFactory())
            .create()

        val response = gson.fromJson(json, WeiboCommentsResponse::class.java)

        assertEquals(1, response.ok)
        val comments = response.data?.data
        assertEquals(2, comments?.size)

        val firstComment = comments!![0]
        assertEquals(5303134956294744L, firstComment.id)
        assertEquals("饿死了饿死了", firstComment.text)
        assertEquals(16, firstComment.floor_number)
        assertEquals("来自上海", firstComment.source)
        assertEquals(false, firstComment.is_mblog_author)

        val badges = firstComment.comment_badge
        assertEquals(1, badges?.size)
        assertEquals("https://h5.sinaimg.cn/badge.png", badges!![0].pic_url)
        assertEquals("loyal_fans", badges[0].name)
        assertEquals(1.33, badges[0].length)

        val dynMsg = firstComment.comment_dynamic_message
        assertEquals("0055", dynMsg?.id)
        assertEquals("https://h5.sinaimg.cn/dynamic.png", dynMsg?.icon_url)
        assertEquals("年费", dynMsg?.tag_text)

        val replies = firstComment.comments
        assertEquals(1, replies?.size)
        val reply = replies!![0]
        assertEquals(5303135330894775L, reply.id)
        assertEquals("我已下单老乡鸡", reply.text)
        assertEquals(true, reply.is_mblog_author)
        assertEquals("来自安徽", reply.source)

        val secondComment = comments[1]
        assertEquals(5303134922475273L, secondComment.id)
        assertEquals("上班迟到了是鸡窝潮湿吗", secondComment.text)
        assertEquals(15, secondComment.floor_number)
        assertEquals("来自浙江", secondComment.source)
        assertEquals(false, secondComment.is_mblog_author)
        assertEquals(null, secondComment.comments)
    }

    @Test
    fun parseWeiboCommentsResponse_acceptsSingularCommentField() {
        val json = """
            {
              "ok": 1,
              "data": {
                "data": [
                  {
                    "created_at": "Wed May 27 10:36:04 +0800 2026",
                    "id": 5303134956294744,
                    "text": "主评论",
                    "total_number": 2,
                    "comment": [
                      {
                        "created_at": "Wed May 27 10:37:33 +0800 2026",
                        "id": 5303135330894775,
                        "text": "楼中楼"
                      }
                    ]
                  }
                ],
                "max_id": 0,
                "max_id_type": 0
              }
            }
        """.trimIndent()

        val gson = com.google.gson.GsonBuilder()
            .registerTypeAdapterFactory(WeiboCommentListTypeAdapterFactory())
            .create()

        val response = gson.fromJson(json, WeiboCommentsResponse::class.java)
        val replies = response.data?.data?.firstOrNull()?.comments

        assertEquals(1, replies?.size)
        assertEquals(2, response.data?.data?.firstOrNull()?.total_number)
        assertEquals(5303135330894775L, replies?.firstOrNull()?.id)
        assertEquals("楼中楼", replies?.firstOrNull()?.text)
    }

    @Test
    fun parseWeiboCommentChildrenResponse_deserializesChildPage() {
        val json = """
            {
              "ok": 1,
              "data": [
                {
                  "created_at": "Tue May 26 14:13:24 +0800 2026",
                  "id": "5302827264248948",
                  "rootid": "5302826358015717",
                  "text": "谢谢！",
                  "source": "来自上海",
                  "like_count": 6,
                  "user": {
                    "id": 1885745060,
                    "screen_name": "实战期货的程序员",
                    "profile_image_url": "https://tvax1.sinaimg.cn/avatar.jpg"
                  }
                }
              ],
              "total_number": 1,
              "max_id": 0,
              "max_id_type": 0
            }
        """.trimIndent()

        val gson = com.google.gson.GsonBuilder()
            .registerTypeAdapterFactory(WeiboCommentListTypeAdapterFactory())
            .create()

        val response = gson.fromJson(json, WeiboCommentChildrenResponse::class.java)
        val reply = response.data?.firstOrNull()

        assertEquals(1, response.ok)
        assertEquals(1, response.total_number)
        assertEquals(5302827264248948L, reply?.id)
        assertEquals("谢谢！", reply?.text)
        assertEquals("实战期货的程序员", reply?.user?.screen_name)
        assertEquals(6, reply?.like_count)
    }

    @Test
    fun parseWeiboRepostChain_handlesNewlinesInUsernames() {
        val raw = """
            遵//@他们叫我冰箱：
            混进来的内鬼一
            盈//@阿喵炸号啦:哪里
            -//@馮偉文:你深圳放就算
            了，顺德你特么放陶陶居？？//@月刊勇者
            KuMa君:不是陶陶居莲香楼也配？//@米芋说:
            草//@韩大狗Jr:ai不ai先放一边，你顺德那么多
            重量级美食结果把陶陶居和莲香楼放到封面上
            是什么意思?
        """.trimIndent()

        val (mainContent, chain) = parseWeiboRepostChain(raw)

        assertEquals("遵", mainContent)
        assertEquals(6, chain.size)

        assertEquals("他们叫我冰箱", chain[0].username)
        assertEquals("混进来的内鬼一\n盈", chain[0].htmlText)

        assertEquals("阿喵炸号啦", chain[1].username)
        assertEquals("哪里\n-", chain[1].htmlText)

        assertEquals("馮偉文", chain[2].username)
        assertEquals("你深圳放就算\n了，顺德你特么放陶陶居？？", chain[2].htmlText)

        assertEquals("月刊勇者KuMa君", chain[3].username)
        assertEquals("不是陶陶居莲香楼也配？", chain[3].htmlText)

        assertEquals("米芋说", chain[4].username)
        assertEquals("草", chain[4].htmlText)

        assertEquals("韩大狗Jr", chain[5].username)
        assertEquals("ai不ai先放一边，你顺德那么多\n重量级美食结果把陶陶居和莲香楼放到封面上\n是什么意思?", chain[5].htmlText)
    }

    @Test
    fun parseWeiboRepostChain_handlesHtmlAnchorAfterSlashes() {
        val raw = """
            <span class="url-icon"><img alt="[doge]" src="doge.png" /></span>//<a href='/n/他们叫我冰箱'>@他们叫我冰箱</a>:<span class="url-icon"><img alt="[泪奔]" src="tear.png" /></span>//<a href='/n/阿喵炸号啦'>@阿喵炸号啦</a>:哪里混进来的内鬼
        """.trimIndent()

        val (mainContent, chain) = parseWeiboRepostChain(raw)

        assertEquals(
            "<span class=\"url-icon\"><img alt=\"[doge]\" src=\"doge.png\" /></span>",
            mainContent
        )
        assertEquals(2, chain.size)
        assertEquals("他们叫我冰箱", chain[0].username)
        assertEquals("<span class=\"url-icon\"><img alt=\"[泪奔]\" src=\"tear.png\" /></span>", chain[0].htmlText)
        assertEquals("阿喵炸号啦", chain[1].username)
        assertEquals("哪里混进来的内鬼", chain[1].htmlText)
    }

    @Test
    fun parseWeiboRepostChain_combinedChainExtraction() {
        val rawHtml = "遵//@他们叫我冰箱:混进来的内鬼一"
        val retweetText = "混进来的内鬼一"
        
        val (mainHtml, statusChain) = parseWeiboRepostChain(rawHtml)
        val (retweetMain, retweetChainList) = parseWeiboRepostChain(retweetText)
        
        val list = mutableListOf<WeiboRepostChainItem>()
        list.addAll(statusChain)
        if (retweetChainList.isNotEmpty()) {
            if (retweetMain.isNotBlank()) {
                list.add(WeiboRepostChainItem("原作者", retweetMain))
            }
            for (i in 0 until retweetChainList.size - 1) {
                list.add(retweetChainList[i])
            }
        }
        
        assertEquals("遵", mainHtml)
        assertEquals(1, list.size)
        assertEquals("他们叫我冰箱", list[0].username)
        assertEquals("混进来的内鬼一", list[0].htmlText)
    }
}
