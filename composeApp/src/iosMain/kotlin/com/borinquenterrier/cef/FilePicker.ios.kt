package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

@Composable
actual fun FilePicker(show: Boolean, onFileSelected: (String?) -> Unit) {
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>
            ) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                onFileSelected(url?.absoluteString)
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                onFileSelected(null)
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

            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
                ?: UIApplication.sharedApplication.windows.mapNotNull { it as? UIWindow }
                    .firstOrNull { it.isKeyWindow() }?.rootViewController

            root?.presentViewController(picker, true, null)
        }
    }
}
