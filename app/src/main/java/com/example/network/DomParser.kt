package com.example.network

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.nodes.Node

sealed class ParsedElement {
    data class TextElement(val text: String, val type: TextType, val link: String? = null) : ParsedElement()
    data class ImageElement(val url: String, val alt: String) : ParsedElement()
}

enum class TextType {
    H1, H2, H3, H4, H5, H6, P, SPAN, A, LI, BLOCKQUOTE, CODE
}

object DomParser {
    fun parsePageAsComponents(html: String, baseUrl: String, fetchImages: Boolean): List<ParsedElement> {
        val document = Jsoup.parse(html, baseUrl)
        // Remove junk elements completely
        document.select("script, style, meta, link, video, audio, iframe, embed, object, noscript, dialog, template, svg, nav, footer, aside, .ad, .ads, .advertisement, [id*=ad-], [class*=ad-]").remove()

        val components = mutableListOf<ParsedElement>()

        // Find the main content area (heuristics)
        var mainContent = document.selectFirst("main, article, [role=main]")
        if (mainContent == null) {
            mainContent = document.body()
        }

        if (mainContent != null) {
            traverseNode(mainContent, components, fetchImages, baseUrl)
        }

        return components
    }

    private fun traverseNode(node: Node, components: MutableList<ParsedElement>, fetchImages: Boolean, baseUrl: String) {
        if (node is TextNode) {
            val text = node.text().trim()
            if (text.isNotEmpty()) {
                val parentNodeName = node.parent()?.nodeName() ?: "p"
                val linkUrl = if (parentNodeName == "a") {
                    node.parent()?.attr("abs:href")
                } else null
                components.add(ParsedElement.TextElement(text, mapNodeType(parentNodeName), linkUrl))
            }
        } else if (node is Element) {
            val tagName = node.tagName().lowercase()
            
            // Skip hidden elements
            val style = node.attr("style").lowercase()
            if (style.contains("display: none") || style.contains("display:none")) return

            when (tagName) {
                "img" -> {
                    if (fetchImages) {
                        var src = node.attr("abs:src")
                        if (src.isEmpty()) {
                            src = node.attr("abs:data-src")
                        }
                        if (src.isNotEmpty()) {
                            components.add(ParsedElement.ImageElement(src, node.attr("alt")))
                        }
                    }
                }
                "br" -> {
                    components.add(ParsedElement.TextElement("\n", TextType.P))
                }
                else -> {
                    for (child in node.childNodes()) {
                        traverseNode(child, components, fetchImages, baseUrl)
                    }
                }
            }
        }
    }

    private fun mapNodeType(tagName: String): TextType {
        return when (tagName.lowercase()) {
            "h1" -> TextType.H1
            "h2" -> TextType.H2
            "h3" -> TextType.H3
            "h4" -> TextType.H4
            "h5" -> TextType.H5
            "h6" -> TextType.H6
            "p" -> TextType.P
            "span" -> TextType.SPAN
            "a" -> TextType.A
            "li" -> TextType.LI
            "blockquote" -> TextType.BLOCKQUOTE
            "code", "pre" -> TextType.CODE
            else -> TextType.P
        }
    }
}
