package org.projectforge.business.fibu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.projectforge.business.configuration.ConfigurationService;
import org.projectforge.framework.i18n.I18nHelper;
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext;
import org.projectforge.framework.time.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Created by blumenstein on 08.05.17.
 * <p>
 * Copy some code from https://stackoverflow.com/questions/22268898/replacing-a-text-in-apache-poi-xwpf
 */
@Service
public class InvoiceService
{
  private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(InvoiceService.class);

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ApplicationContext applicationContext;

  @Value("${projectforge.invoiceTemplate}")
  private String customInvoiceTemplateName;

  public ByteArrayOutputStream getInvoiceWordDocument(final RechnungDO data)
  {
    ByteArrayOutputStream result = null;
    try {
      Resource invoiceTemplate = null;
      boolean isSkonto = data.getDiscountMaturity() != null && data.getDiscountPercent() != null && data.getDiscountZahlungsZielInTagen() != null;
      if (customInvoiceTemplateName.isEmpty() == false) {
        String resourceDir = configurationService.getResourceDir();
        invoiceTemplate = applicationContext
            .getResource("file://" + resourceDir + "/officeTemplates/" + customInvoiceTemplateName + (isSkonto ? "_Skonto" : "") + ".docx");
      }
      if (invoiceTemplate == null || invoiceTemplate.exists() == false) {
        invoiceTemplate = applicationContext.getResource("classpath:officeTemplates/InvoiceTemplate" + (isSkonto ? "_Skonto" : "") + ".docx");
      }
      XWPFDocument templateDocument = readWordFile(invoiceTemplate.getInputStream());
      Map<String, String> map = new HashMap<>();
      map.put("Rechnungsadresse", data.getCustomerAddress());
      map.put("Typ", data.getTyp() != null ? I18nHelper.getLocalizedMessage(data.getTyp().getI18nKey()) : "");
      map.put("Kundenreferenz", data.getCustomerref1());
      map.put("Kundenreferenz2", data.getCustomerref2());
      map.put("Auftragsnummer", data.getPositionen().stream()
          .filter(pos -> pos.getAuftragsPosition() != null && pos.getAuftragsPosition().getAuftrag() != null)
          .map(pos -> String.valueOf(pos.getAuftragsPosition().getAuftrag().getNummer()))
          .distinct()
          .collect(Collectors.joining(", ")));
      map.put("VORNAME_NACHNAME", ThreadLocalUserContext.getUser() != null && ThreadLocalUserContext.getUser().getFullname() != null ?
          ThreadLocalUserContext.getUser().getFullname().toUpperCase() :
          "");
      map.put("Rechnungsnummer", data.getNummer() != null ? data.getNummer().toString() : "");
      map.put("Rechnungsdatum", DateTimeFormatter.instance().getFormattedDate(data.getDatum()));
      map.put("Faelligkeit", DateTimeFormatter.instance().getFormattedDate(data.getFaelligkeit()));
      if (isSkonto) {
        map.put("Skonto", formatBigDecimal(data.getDiscountPercent()) + " %");
        map.put("Faelligkeit_Skonto", DateTimeFormatter.instance().getFormattedDate(data.getDiscountMaturity()));
      }
      replaceInWholeDocument(templateDocument, map);

      replaceInPosTable(templateDocument, data);

      result = new ByteArrayOutputStream();
      templateDocument.write(result);
    } catch (IOException e) {
      log.error("Could not read invoice template", e);
    }
    return result;
  }

  private void replaceInWholeDocument(XWPFDocument document, Map<String, String> map)
  {
    List<XWPFParagraph> paragraphs = document.getParagraphs();
    for (XWPFParagraph paragraph : paragraphs) {
      if (StringUtils.isEmpty(paragraph.getText()) == false) {
        replaceInParagraph(paragraph, map);
      }
      replaceInTable(document, map);
    }
  }

  private void replaceInWholeDocument(XWPFDocument document, String searchText, String replacement)
  {
    List<XWPFParagraph> paragraphs = document.getParagraphs();
    for (XWPFParagraph paragraph : paragraphs) {
      if (StringUtils.isEmpty(paragraph.getText()) == false && StringUtils.contains(paragraph.getText(), searchText)) {
        replaceInParagraph(paragraph, "{" + searchText + "}", replacement);
      }
      replaceInTable(document, "{" + searchText + "}", replacement);
    }
  }

  private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> map)
  {
    for (Map.Entry<String, String> entry : map.entrySet()) {
      String searchText = "{" + entry.getKey() + "}";
      if (StringUtils.isEmpty(paragraph.getText()) == false && StringUtils.contains(paragraph.getText(), searchText)) {
        replaceInParagraph(paragraph, searchText, entry.getValue() != null ? entry.getValue() : "");
      }
    }
  }

  private void replaceInParagraph(XWPFParagraph paragraph, String searchText, String replacement)
  {
    boolean found = true;
    while (found) {
      found = false;
      int pos = paragraph.getText().indexOf(searchText);
      if (pos >= 0) {
        found = true;
        Map<Integer, XWPFRun> posToRuns = getPosToRuns(paragraph);
        XWPFRun run = posToRuns.get(pos);
        XWPFRun lastRun = posToRuns.get(pos + searchText.length() - 1);
        int runNum = paragraph.getRuns().indexOf(run);
        int lastRunNum = paragraph.getRuns().indexOf(lastRun);
        if (replacement.contains("\r\n")) {
          replacement = replacement.replace("\r", "");
        }
        int runCount = paragraph.getRuns().size();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i <= runCount; i++) {
          if (i >= runNum && i <= lastRunNum) {
            sb.append(paragraph.getRuns().get(i).getText(0));
          }
        }
        String newText = sb.toString().replace(searchText, replacement);
        run.setText(newText, 0);
        for (int i = lastRunNum; i > runNum; i--) {
          paragraph.removeRun(i);
        }
      }
    }
  }

  private void replaceInTable(XWPFDocument document, Map<String, String> map)
  {
    for (Map.Entry<String, String> entry : map.entrySet()) {
      String searchText = "{" + entry.getKey() + "}";
      replaceInTable(document, searchText, entry.getValue() != null ? entry.getValue() : "");
    }
  }

  private void replaceInTable(XWPFDocument document, String searchText, String replacement)
  {
    for (XWPFTable tbl : document.getTables()) {
      for (XWPFTableRow row : tbl.getRows()) {
        for (XWPFTableCell cell : row.getTableCells()) {
          for (XWPFParagraph paragraph : cell.getParagraphs()) {
            replaceInParagraph(paragraph, searchText, replacement);
          }
        }
      }
    }
  }

  private Map<Integer, XWPFRun> getPosToRuns(XWPFParagraph paragraph)
  {
    int pos = 0;
    Map<Integer, XWPFRun> map = new HashMap<>(10);
    for (XWPFRun run : paragraph.getRuns()) {
      String runText = run.text();
      if (runText != null) {
        for (int i = 0; i < runText.length(); i++) {
          map.put(pos + i, run);
        }
        pos += runText.length();
      }
    }
    return map;
  }

  private void replaceInPosTable(final XWPFDocument templateDocument, final RechnungDO invoice)
  {
    XWPFTable posTbl = generatePosTableRows(templateDocument, invoice.getPositionen());
    replacePosDataInTable(posTbl, invoice);
    replaceSumDataInTable(posTbl, invoice);
  }

  private void replaceSumDataInTable(final XWPFTable posTbl, final RechnungDO invoice)
  {
    Map<String, String> map = new HashMap<>();
    map.put("Zwischensumme", formatBigDecimal(invoice.getNetSum()));
    map.put("MwSt", formatBigDecimal(invoice.getVatAmountSum()));
    map.put("Gesamtbetrag", formatBigDecimal(invoice.getGrossSum()));
    int tableRowSize = posTbl.getRows().size();
    for (int startSumRow = tableRowSize - 2; startSumRow < tableRowSize; startSumRow++) {
      for (XWPFTableCell cell : posTbl.getRow(startSumRow).getTableCells()) {
        for (XWPFParagraph cellParagraph : cell.getParagraphs()) {
          replaceInParagraph(cellParagraph, map);
        }
      }
    }
  }

  private String formatBigDecimal(final BigDecimal value)
  {
    if (value == null) {
      return "";
    }
    DecimalFormat df = new DecimalFormat("#,###.00");
    return df.format(value.setScale(2));
  }

  private void replacePosDataInTable(final XWPFTable posTbl, final RechnungDO invoice)
  {
    int rowCount = 1;
    for (RechnungsPositionDO position : invoice.getPositionen()) {
      String identifier = "{" + position.getNumber() + "}";
      Map<String, String> map = new HashMap<>();
      map.put(identifier + "Posnummer", String.valueOf(position.getNumber()));
      map.put(identifier + "Text", position.getText());
      map.put(identifier + "Leistungszeitraum", getPeriodOfPerformance(position, invoice));
      map.put(identifier + "Menge", formatBigDecimal(position.getMenge()));
      map.put(identifier + "Einzelpreis", formatBigDecimal(position.getEinzelNetto()));
      map.put(identifier + "Betrag", formatBigDecimal(position.getNetSum()));
      for (XWPFTableCell cell : posTbl.getRow(rowCount).getTableCells()) {
        for (XWPFParagraph cellParagraph : cell.getParagraphs()) {
          replaceInParagraph(cellParagraph, map);
        }
      }
      rowCount++;
    }
  }

  private String getPeriodOfPerformance(final RechnungsPositionDO position, final RechnungDO invoice)
  {
    if (position.getPeriodOfPerformanceType().equals(PeriodOfPerformanceType.OWN)) {
      return DateTimeFormatter.instance().getFormattedDate(position.getPeriodOfPerformanceBegin()) + " - " + DateTimeFormatter.instance()
          .getFormattedDate(position.getPeriodOfPerformanceEnd());
    } else {
      return DateTimeFormatter.instance().getFormattedDate(invoice.getPeriodOfPerformanceBegin()) + " - " + DateTimeFormatter.instance()
          .getFormattedDate(invoice.getPeriodOfPerformanceEnd());
    }
  }

  private XWPFTable generatePosTableRows(final XWPFDocument templateDocument, final List<RechnungsPositionDO> positionen)
  {
    XWPFTable posTbl = null;
    for (XWPFTable tbl : templateDocument.getTables()) {
      if (tbl.getRow(0).getCell(0).getText().contains("Beschreibung")) {
        posTbl = tbl;
      }
    }
    for (int i = 2; i <= positionen.size(); i++) {
      copyTableRow(posTbl, i);
    }
    int rowCount = 1;
    for (RechnungsPositionDO position : positionen) {
      for (XWPFTableCell cell : posTbl.getRow(rowCount).getTableCells()) {
        for (XWPFParagraph cellParagraph : cell.getParagraphs()) {
          replaceInParagraph(cellParagraph, "id", String.valueOf(position.getNumber()));
        }
      }
      rowCount++;
    }
    return posTbl;
  }

  private void copyTableRow(final XWPFTable posTbl, final int rowCounter)
  {
    XWPFTableRow rowToCopy = posTbl.getRow(1);
    CTRow row = posTbl.getCTTbl().insertNewTr(rowCounter);
    row.set(rowToCopy.getCtRow());
    XWPFTableRow copyRow = new XWPFTableRow(row, posTbl);
    posTbl.getRows().add(rowCounter, copyRow);
  }

  private XWPFDocument readWordFile(InputStream is)
  {
    try {
      XWPFDocument docx = new XWPFDocument(is);
      return docx;
    } catch (IOException e) {
      log.error("Exception while reading docx file.", e);
    }
    return null;
  }

}
