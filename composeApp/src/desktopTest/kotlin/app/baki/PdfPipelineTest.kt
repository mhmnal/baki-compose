package app.baki

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end check of the real pipeline: PDF bytes -> text -> Statement.
 * Skipped unless BAKI_PDF points at a statement, so no personal document enters the repo.
 */
class PdfPipelineTest {

    @Test
    fun sniffsPdfHeadersEvenWithLeadingBytes() {
        assertTrue(looksLikePdf("%PDF-1.7\n".encodeToByteArray()))
        assertTrue(looksLikePdf(byteArrayOf(0, 0, 0) + "%PDF-1.4".encodeToByteArray()))
        assertFalse(looksLikePdf("not a pdf".encodeToByteArray()))
        assertFalse(looksLikePdf(ByteArray(0)))
    }

    @Test
    fun readsARealGrabPdf() {
        val path = System.getenv("BAKI_PDF") ?: return
        val text = assertNotNull(PdfText.extract(File(path).readBytes()), "PDFBox returned no text")
        val st = assertNotNull(StatementParse.parse(text), "parser rejected the PDF text")
        println("[pdf] ${st.weekStart} income=${st.income} deductions=${st.deductions} " +
            "weekly=${st.weekly} insurance=${st.insuranceCount} consistent=${st.consistent}")
        assertEquals(507.34, st.income, 0.01)
        assertEquals(469.84, st.weekly, 0.01)
        assertEquals(5, st.insuranceCount)
    }
}
