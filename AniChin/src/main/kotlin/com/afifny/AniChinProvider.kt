package com.afifny

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf

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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(request.data+page).document
        val home = document.select("div.")
        return super.getMainPage(page, request)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return listOf<SearchResponse>()
    }
}