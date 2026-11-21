package eu.kanade.tachiyomi.util.manga

import tachiyomi.domain.manga.model.Manga

/**
 * Genre tags commonly used by sources (MangaDex, Webtoons, Comick, etc.) to mark
 * series that are published as a single continuous vertical strip instead of
 * discrete left/right pages (traditional manga).
 */
private val LONG_STRIP_GENRE_KEYWORDS = listOf(
    "long strip",
    "webtoon",
    "manhwa",
    "manhua",
    "مانهو",
    "ويبتون",
)

/**
 * Best-effort guess at whether this manga should be read as a continuous vertical
 * strip (webtoon/manhwa/manhua) rather than paged left-to-right or right-to-left
 * (traditional manga), based on the genre tags the source reports for it.
 *
 * This is only a heuristic: some manhua/manhwa titles are still paged, and some
 * sources omit these genre tags entirely. A manual per-manga choice made from the
 * in-reader reading mode menu always takes priority over this detection
 * (see [eu.kanade.tachiyomi.ui.reader.ReaderViewModel.getMangaReadingMode]).
 */
fun Manga.isLikelyLongStrip(): Boolean {
    val genres = genre ?: return false
    return genres.any { genre ->
        LONG_STRIP_GENRE_KEYWORDS.any { keyword -> genre.contains(keyword, ignoreCase = true) }
    }
}
