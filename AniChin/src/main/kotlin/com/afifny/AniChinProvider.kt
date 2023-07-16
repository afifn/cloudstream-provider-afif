package com.afifny

import com.lagradost.cloudstream3.*
import org.jsoup.nodes.Element

class AniChinProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://anichin.top/"
    override var name = "AniChin"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "id"
    override val hasDownloadSupport = true
    override val hasMainPage = true


    override val mainPage = mainPageOf(
        "$mainUrl/anime/?status=&type=&order=update" to "Rilis Terbaru",
        "$mainUrl/anime/?status=&type=Movie&order=update" to "Movie Terbaru",
        "$mainUrl/ongoing/" to "Anime Ongoing",
        "$mainUrl/completed/" to "Donghua Complete",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("div.post-show > article, div.relat > article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf<SearchResponse>()
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val href = getProperAnimeLink(fixUrlNull(this.selectFirst("a")?.attr("href")).toString())
        val title = this.select(".tt > h2").text().trim()
        val posterUrl = fixUrlNull(this.selectFirst("div.limit img")?.attr("src"))
        val epNum = this.selectFirst(".tt > h2")?.text()?.let {
            Regex("Episode\\s?(\\d+)").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }
    }
    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            var title = uri.substringAfter("$mainUrl/")
            title = when {
                (title.contains("-episode")) && !(title.contains("-movie")) -> title.substringBefore(
                    "-episode"
                )
                (title.contains("-movie")) -> title.substringBefore("-movie")
                else -> title
            }

            "$mainUrl/anime/$title"
        }
    }
}


