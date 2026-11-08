package io.github.grillza.icreader

import android.app.Application
import io.github.grillza.icreader.data.CardRepository
import io.github.grillza.icreader.data.StationDb
import io.github.grillza.icreader.icon.AppIconManager

class IcReaderApp : Application() {
    val repository by lazy { CardRepository(this) }
    val stations by lazy { StationDb({ assets.open("StationCode.csv") }, { assets.open("StationNamesEn.csv") }) }
    val icons by lazy { AppIconManager(this) }
}
