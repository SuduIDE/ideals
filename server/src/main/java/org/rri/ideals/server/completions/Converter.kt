package org.rri.ideals.server.completions

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.psi.xml.XmlTokenType

class Converter() : XmlRecursiveElementVisitor() {
    fun XmlTag.attributesAsString() = if (attributes.isNotEmpty())
        attributes.joinToString(separator = " ", prefix = " ") { it.text }
    else
        ""

    private enum class ListType { Ordered, Unordered; }
    data class MarkdownSpan(val prefix: String, val suffix: String) {
        companion object {
            val Empty = MarkdownSpan("", "")

            fun wrap(text: String) = MarkdownSpan(text, text)
            fun prefix(text: String) = MarkdownSpan(text, "")

            fun preserveTag(tag: XmlTag) =
                MarkdownSpan("<${tag.name}${tag.attributesAsString()}>", "</${tag.name}>")
        }
    }


    val result: String
        get() = markdownBuilder.toString()

    private val markdownBuilder = StringBuilder("/**")
    private var afterLineBreak = false
    private var whitespaceIsPartOfText = true
    private var currentListType = ListType.Unordered

    override fun visitWhiteSpace(space: PsiWhiteSpace) {
        super.visitWhiteSpace(space)

        if (whitespaceIsPartOfText) {
            appendPendingText()
            val lines = space.text.lines()
            if (lines.size == 1) {
                markdownBuilder.append(space.text)
            } else {
                //several lines of spaces:
                //drop first line - it contains trailing spaces before the first new-line;
                //do not add star for the last line, it is handled by appendPendingText()
                //and it is not needed in the end of the comment
                lines.drop(1).dropLast(1).forEach {
                    markdownBuilder.append("\n * ")
                }
                markdownBuilder.append("\n")
                afterLineBreak = true
            }
        }
    }

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)

        when (element.node.elementType) {
            XmlTokenType.XML_DATA_CHARACTERS -> {
                appendPendingText()
                markdownBuilder.append(element.text)
            }
            XmlTokenType.XML_CHAR_ENTITY_REF -> {
                appendPendingText()
                val grandParent = element.parent.parent
                if (grandParent is HtmlTag && (grandParent.name == "code" || grandParent.name == "literal"))
                    markdownBuilder.append(StringUtil.unescapeXmlEntities(element.text))
                else
                    markdownBuilder.append(element.text)
            }
        }

    }

    override fun visitXmlTag(tag: XmlTag) {
        withWhitespaceAsPartOfText(false) {
            val oldListType = currentListType
            val atLineStart = afterLineBreak
            appendPendingText()
            val (openingMarkdown, closingMarkdown) = getMarkdownForTag(tag, atLineStart)
            markdownBuilder.append(openingMarkdown)

            super.visitXmlTag(tag)

            //appendPendingText()
            markdownBuilder.append(closingMarkdown)
            currentListType = oldListType
        }
    }

    override fun visitXmlText(text: XmlText) {
        withWhitespaceAsPartOfText(true) {
            super.visitXmlText(text)
        }
    }

    private inline fun withWhitespaceAsPartOfText(newValue: Boolean, block: () -> Unit) {
        val oldValue = whitespaceIsPartOfText
        whitespaceIsPartOfText = newValue
        try {
            block()
        } finally {
            whitespaceIsPartOfText = oldValue
        }
    }

    private fun getMarkdownForTag(tag: XmlTag, atLineStart: Boolean): MarkdownSpan = when (tag.name) {
        "b", "strong" -> MarkdownSpan.wrap("**")

        "p" -> if (atLineStart) MarkdownSpan.prefix("\n * ") else MarkdownSpan.prefix("\n *\n *")

        "i", "em" -> MarkdownSpan.wrap("*")

        "s", "del" -> MarkdownSpan.wrap("~~")

        "code" -> {
            val innerText = tag.value.text.trim()
            if (innerText.startsWith('`') && innerText.endsWith('`'))
                MarkdownSpan("`` ", " ``")
            else
                MarkdownSpan.wrap("`")
        }

        "a" -> {
            if (tag.getAttributeValue("docref") != null) {
                val docRef = tag.getAttributeValue("docref")
                val innerText = tag.value.text
                if (docRef == innerText) MarkdownSpan("[", "]") else MarkdownSpan("[", "][$docRef]")
            } else if (tag.getAttributeValue("href") != null) {
                MarkdownSpan("[", "](${tag.getAttributeValue("href") ?: ""})")
            } else {
                MarkdownSpan.preserveTag(tag)
            }
        }

        "ul" -> {
            currentListType = ListType.Unordered; MarkdownSpan.Empty
        }

        "ol" -> {
            currentListType = ListType.Ordered; MarkdownSpan.Empty
        }

        "li" -> if (currentListType == ListType.Unordered) MarkdownSpan.prefix(" * ") else MarkdownSpan.prefix(" 1. ")

        else -> MarkdownSpan.preserveTag(tag)
    }

    private fun appendPendingText() {
        if (afterLineBreak) {
            markdownBuilder.append(" * ")
            afterLineBreak = false
        }
    }

    override fun visitXmlFile(file: XmlFile) {
        super.visitXmlFile(file)

        markdownBuilder.append(" */")
    }
}