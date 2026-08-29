package com.example.foiltracker.sharing

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.foiltracker.data.TrackFile
import java.io.File

object FileSharing {

    fun share(
        context: Context,
        track: TrackFile
    ) {

        val file =
            File(track.localPath)


        if (!file.exists()) {
            return
        }


        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )


        val intent =
            Intent(Intent.ACTION_SEND)
                .apply {

                    type =
                        track.mimeType

                    putExtra(
                        Intent.EXTRA_STREAM,
                        uri
                    )

                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        track.filename
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }


        context.startActivity(
            Intent.createChooser(
                intent,
                "Share GPX"
            )
        )
    }
}
