package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

@Composable
actual fun FilePicker(show: Boolean, onFilesSelected: (List<String>) -> Unit) {
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>
            ) {
                val urls = didPickDocumentsAtURLs.mapNotNull { (it as? NSURL)?.absoluteString }
                onFilesSelected(urls)
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                onFilesSelected(emptyList())
            }
        }
    }

    LaunchedEffect(show) {
        if (show) {
            val types = listOfNotNull(
                UTType.typeWithIdentifier("org.openxmlformats.wordprocessingml.document")
                    ?: UTType.typeWithFilenameExtension("docx"),
                UTTypePDF,
                UTTypePlainText,
                UTType.typeWithIdentifier("public.calendar")
                    ?: UTType.typeWithFilenameExtension("ics")
            )

            val picker =
                UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
            picker.delegate = delegate
            picker.allowsMultipleSelection = true

            findKeyWindow()?.rootViewController?.presentViewController(picker, true, null)
        }
    }
}
