package com.raival.compose.file.explorer.screen.viewer.document.parser

import com.raival.compose.file.explorer.common.emptyString
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.sl.usermodel.TextShape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.InputStream

/** Satu blok konten dokumen Word: paragraf biasa atau tabel. */
sealed interface DocumentBlock {
    data class Paragraph(val text: String, val isHeading: Boolean = false) : DocumentBlock
    data class Table(val rows: List<List<String>>) : DocumentBlock
}

/** Isi satu sheet spreadsheet; sel kosong tetap ditempatkan agar kolom lurus. */
data class SheetContent(val name: String, val rows: List<List<String>>)

/** Isi satu slide presentasi; teks pertama dipakai sebagai judul. */
data class SlideContent(val number: Int, val title: String, val body: List<String>)

/**
 * Konten dokumen yang sudah diurai. Jenisnya menentukan tampilan di layar:
 * Word dibaca per blok, spreadsheet per sheet, presentasi per slide.
 */
sealed interface DocumentContent {
    data class WordDocument(val blocks: List<DocumentBlock>) : DocumentContent
    data class Spreadsheet(val sheets: List<SheetContent>) : DocumentContent
    data class Presentation(val slides: List<SlideContent>) : DocumentContent
}

/**
 * Mengurai dokumen Microsoft Office modern (.docx, .xlsx, .pptx) menjadi teks
 * terstruktur memakai Apache POI. Input diharapkan sudah dibuka dari uri
 * berkas oleh pemanggil.
 */
object DocumentParser {

    fun parse(extension: String, input: InputStream): DocumentContent = when (extension) {
        "docx" -> parseDocx(input)
        "xlsx" -> parseSpreadsheet(input)
        "pptx" -> parsePptx(input)
        else -> throw IllegalArgumentException("Format dokumen tidak didukung: $extension")
    }

    /** .docx: paragraf dan tabel dibaca berurutan sesuai isi dokumen. */
    private fun parseDocx(input: InputStream): DocumentContent.WordDocument {
        XWPFDocument(input).use { document ->
            val blocks = arrayListOf<DocumentBlock>()

            document.bodyElements.forEach { element ->
                when (element) {
                    is XWPFParagraph -> {
                        val text = element.text.trim()
                        if (text.isNotEmpty()) {
                            // Gaya "Heading1" dan sejenisnya ditandai sebagai judul.
                            val style = element.styleID ?: emptyString
                            blocks.add(
                                DocumentBlock.Paragraph(
                                    text = text,
                                    isHeading = style.lowercase().startsWith("heading")
                                )
                            )
                        }
                    }

                    is XWPFTable -> {
                        val rows = element.rows.map { row ->
                            row.tableCells.map { it.text.trim() }
                        }
                        if (rows.isNotEmpty()) blocks.add(DocumentBlock.Table(rows))
                    }
                }
            }

            return DocumentContent.WordDocument(blocks)
        }
    }

    /** .xlsx: setiap sheet menjadi daftar baris berisi nilai sel berformat. */
    private fun parseSpreadsheet(input: InputStream): DocumentContent.Spreadsheet {
        WorkbookFactory.create(input).use { workbook ->
            val formatter = DataFormatter()
            val evaluator = workbook.creationHelper.createFormulaEvaluator()

            val sheets = workbook.map { sheet ->
                val rows = sheet.rowIterator().asSequence().map { row ->
                    (0 until row.lastCellNum.toInt()).map { index ->
                        val cell = row.getCell(index)
                        if (cell == null) ""
                        else runCatching {
                            formatter.formatCellValue(cell, evaluator)
                        }.getOrDefault("")
                    }.dropLastWhile { it.isBlank() }
                }.filter { it.isNotEmpty() }
                    .toList()

                SheetContent(name = sheet.sheetName, rows = rows)
            }.filter { it.rows.isNotEmpty() }

            return DocumentContent.Spreadsheet(sheets)
        }
    }

    /** .pptx: teks pertama pada tiap slide dipakai sebagai judulnya. */
    private fun parsePptx(input: InputStream): DocumentContent.Presentation {
        XMLSlideShow(input).use { slideshow ->
            val slides = slideshow.slides.mapIndexed { index, slide ->
                val texts = arrayListOf<String>()

                slide.shapes.forEach { shape ->
                    (shape as? TextShape<*, *>)
                        ?.text
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { texts.add(it) }
                }

                SlideContent(
                    number = index + 1,
                    title = texts.firstOrNull().orEmpty(),
                    body = texts.drop(1)
                )
            }

            return DocumentContent.Presentation(slides)
        }
    }
}
