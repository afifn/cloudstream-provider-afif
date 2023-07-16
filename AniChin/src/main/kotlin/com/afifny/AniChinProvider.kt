package com.afifny

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.Jsoup
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
        val home = document.select("div.excstf > article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }
    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = app.get(link, headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).document

        return document.select("div.listupd article").mapNotNull {
            it.toSearchResult()
        }
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
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text().toString()
            .replace("Subtitle Indonesia", "").trim()
        val poster = document.selectFirst("div.entry-content > img")?.attr("src")
        val type = document.select("tbody th:contains(Tipe)").next().text().lowercase()
        val year = document.select("tbody th:contains(Dirilis)").next().text().trim().toIntOrNull()

        val episodes = document.select("ul.daftar > li").map {
            val link = fixUrl(it.select("a").attr("href"))
            val name = it.select("a").text()
            val episode = Regex("Episode\\s?(\\d+)").find(name)?.groupValues?.getOrNull(0)?.toIntOrNull()
            Episode(link, name, episode = episode)
        }.reversed()

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
//            showStatus =  getStatus(document.select("tbody th:contains(Status)").next().text().trim())
            plot = document.selectFirst("div.entry-content > p")?.text()
            this.tags =
                document.select("tbody th:contains(Genre)").next().select("a").map { it.text() }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        document.select(".mobius > .mirror > option").apmap {
            safeApiCall {
                val iframe = fixUrl(
                    Jsoup.parse(base64Decode(it.attr("data-em"))).select("iframe").attr("src")
                        ?: throw ErrorLoadingException("No iframe found")
                )

                when {
                    iframe.startsWith("$mainUrl/utils/player/arch/") || iframe.startsWith(
                        "$mainUrl/utils/player/race/"
                    ) -> request(iframe, ref = data).document.select("source").attr("src")
                        .let { link ->
                            val source =
                                when {
                                    iframe.contains("/arch/") -> "Arch"
                                    iframe.contains("/race/") -> "Race"
                                    else -> this.name
                                }
                            val quality =
                                Regex("\\.(\\d{3,4})\\.").find(link)?.groupValues?.get(1)
                            callback.invoke(
                                ExtractorLink(
                                    source = source,
                                    name = source,
                                    url = link,
                                    referer = mainUrl,
                                    quality = quality?.toIntOrNull() ?: Qualities.Unknown.value
                                )
                            )
                        }
//                    skip for now
//                    iframe.startsWith("$mainUrl/utils/player/fichan/") -> ""
//                    iframe.startsWith("$mainUrl/utils/player/blogger/") -> ""
                    iframe.startsWith("https://aghanim.xyz/tools/redirect/") -> {
                        val link = "https://rasa-cintaku-semakin-berantai.xyz/v/${
                            iframe.substringAfter("id=").substringBefore("&token")
                        }"
                        loadExtractor(link, mainUrl, subtitleCallback, callback)
                    }
                    iframe.startsWith("$mainUrl/utils/player/framezilla/") || iframe.startsWith("https://uservideo.xyz") -> {
                        request(iframe, ref = data).document.select("iframe").attr("src")
                            .let { link ->
                                loadExtractor(fixUrl(link), mainUrl, subtitleCallback, callback)
                            }
                    }
                    else -> {
                        loadExtractor(iframe, mainUrl, subtitleCallback, callback)
                    }
                }
            }
        }

        return true
    }

    private suspend fun request(url: String, ref: String? = null): NiceResponse {
        return app.get(
            url,
            headers = mapOf("Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"),
            cookies = mapOf("_as_ipin_ct" to "ID"),
            referer = ref
        )
    }
}


