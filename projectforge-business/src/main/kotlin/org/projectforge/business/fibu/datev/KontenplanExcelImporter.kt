/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2019 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////
package org.projectforge.business.fibu.datev

import de.micromata.merlin.excel.ExcelColumnName
import de.micromata.merlin.excel.ExcelColumnNumberValidator
import de.micromata.merlin.excel.ExcelSheet
import de.micromata.merlin.excel.ExcelWorkbook
import de.micromata.merlin.excel.importer.ImportStorage
import de.micromata.merlin.excel.importer.ImportedSheet
import org.projectforge.business.fibu.KontoDO
import org.projectforge.framework.i18n.UserException
import org.projectforge.framework.persistence.utils.MyImportedElement
import org.slf4j.LoggerFactory
import java.io.InputStream

class KontenplanExcelImporter {
    private enum class Cols(override val head: String, override vararg val aliases: String) : ExcelColumnName {
        KONTO("Konto", "Konto von"),
        BEZEICHNUNG("Bezeichnung", "Beschriftung")
    }

    fun doImport(storage: ImportStorage<KontoDO>, inputStream: InputStream) {
        val workbook = ExcelWorkbook(inputStream, storage.filename!!)
        val sheet = workbook.getSheet(NAME_OF_EXCEL_SHEET)
        if (sheet == null) {
            val msg = "Konten können nicht importiert werden: Blatt '$NAME_OF_EXCEL_SHEET' nicht gefunden."
            storage.logger.error(msg)
            throw UserException(msg)
        }
        importKontenplan(storage, sheet)
    }

    private fun importKontenplan(storage: ImportStorage<KontoDO>, sheet: ExcelSheet) {
        sheet.autotrimCellValues = true
        storage.logger.info("Reading sheet '$NAME_OF_EXCEL_SHEET'.")
        sheet.registerColumn(Cols.KONTO, ExcelColumnNumberValidator(1.0).setRequired())
        sheet.registerColumn(Cols.BEZEICHNUNG,ExcelColumnNumberValidator(1.0).setRequired())
        sheet.analyze(true)
        if (sheet.headRow == null) {
            val msg = "Ignoring sheet '$NAME_OF_EXCEL_SHEET' for importing Buchungssätze, no valid head row found."
            log.info(msg)
            storage.logger.info(msg)
            return
        }
        val importedSheet = ImportedSheet<KontoDO>(sheet)
        storage.addSheet(importedSheet)
        importedSheet.name = NAME_OF_EXCEL_SHEET
        importedSheet.logger.addValidationErrors(sheet)
        val it = sheet.dataRowIterator
        val year = 0
        while (it.hasNext()) {
            val row = it.next()
            val element = MyImportedElement(storage.nextVal(), KontoDO::class.java,
                    *DatevImportDao.KONTO_DIFF_PROPERTIES)
            val konto = KontoDO()
            element.value = konto
            konto.nummer = sheet.getCellInt(row, "Konto")
            konto.bezeichnung = sheet.getCellString(row, "Bezeichnung")
            importedSheet.addElement(element)
            log.debug(konto.toString())
        }
    }

    companion object {
        const val NAME_OF_EXCEL_SHEET = "Kontenplan"
        private val log = LoggerFactory.getLogger(KontenplanExcelImporter::class.java)
    }
}
