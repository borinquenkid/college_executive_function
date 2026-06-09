package com.borinquenterrier.cef

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.File

object TestPdfGenerator {
    /**
     * Generates a PDF file at [outputFile] containing the provided [lines] of text.
     */
    fun generatePdf(lines: List<String>, outputFile: File) {
        val doc = PDDocument()
        val page = PDPage()
        doc.addPage(page)
        
        val contentStream = PDPageContentStream(doc, page)
        contentStream.beginText()
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        contentStream.setFont(font, 12f)
        contentStream.setLeading(15f)
        contentStream.newLineAtOffset(50f, 750f)
        
        for (line in lines) {
            // PDFBox showText doesn't handle some special characters nicely or newlines,
            // so we draw line by line.
            contentStream.showText(line)
            contentStream.newLine()
        }
        
        contentStream.endText()
        contentStream.close()
        
        outputFile.parentFile.mkdirs()
        doc.save(outputFile)
        doc.close()
    }
}
