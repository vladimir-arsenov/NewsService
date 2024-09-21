package dsl

fun main() {
    val newsList = yamlNewsList {
        news {
            title { +"Something happened"}
            description { +"This happened" }
            rating { +"24" }
            commentsCount { +"5" }
        }
        news {
            title { +"Nothing really happened"}
            rating{ +"0.5"}
            commentsCount { +"3" }
            description {
                val nothing = ""
                +nothing
            }
        }
    }
    println(newsList)
}

@DslMarker
annotation class ElementMarker

interface Element {
    fun render(builder: StringBuilder, indent: String)
}

class TextElement(private val text: String) : Element {
    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent$text\n")
    }
}

@ElementMarker
abstract class Key(private val name: String) : Element {
    val children = arrayListOf<Element>()
    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent$name:\n")
        for (child in children) {
            child.render(builder, "$indent  ")
        }
    }

    protected fun <T : Element> initTag(tag: T, init: T.() -> Unit): T {
        tag.init()
        children.add(tag)
        return tag
    }

    override fun toString(): String {
        val builder = StringBuilder()
        render(builder, "")
        return builder.toString()
    }

}

abstract class KeyValue(name: String) : Key(name) {
    operator fun String.unaryPlus() {
        children.add(TextElement(this))
    }


}

class NewsList : Key("newsList") {
    fun news(init: NewsItem.() -> Unit) = initTag(NewsItem(), init)
}

class NewsItem : Key("news") {
    fun rating (init: Rating.() -> Unit) = initTag(Rating(), init)
    fun commentsCount(init: CommentsCount.() -> Unit) = initTag(CommentsCount(), init)
    fun title(init: Title.() -> Unit) = initTag(Title(), init)
    fun place(init: Place.() -> Unit) = initTag(Place(), init)
    fun id(init: Id.() -> Unit) = initTag(Id(), init)
    fun favoritesCount(init: FavoritesCount.() -> Unit) = initTag(FavoritesCount(), init)
    fun publicationDate(init: PublicationDate.() -> Unit) = initTag(PublicationDate(), init)
    fun siteUrl(init: SiteUrl.() -> Unit) = initTag(SiteUrl(), init)
    fun description(init: Description.() -> Unit) = initTag(Description(), init)
}

class Rating : KeyValue("rating")
class CommentsCount : KeyValue("commentsCount")
class Title : KeyValue("title")
class Place : KeyValue("place")
class Id : KeyValue("id")
class FavoritesCount : KeyValue("favoritesCount")
class Description : KeyValue("description")
class PublicationDate : KeyValue("publicationDate")
class SiteUrl : KeyValue("siteUrl")

fun yamlNewsList(init: NewsList.() -> Unit): NewsList {
    val newsList = NewsList()
    newsList.init()
    return newsList
}