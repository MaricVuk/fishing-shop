package rs.fishingshop.invoices;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;
import rs.fishingshop.orders.RsdFormat;

@Component
class InvoicePdf {

    record Line(String productName, String sku, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    record Document(
            String number,
            Instant issuedAt,
            long orderId,
            String customerName,
            String customerAddress,
            String customerEmail,
            String customerPhone,
            List<Line> lines,
            BigDecimal total,
            Vat vat,
            BigDecimal vatRate,
            InvoiceProperties seller) {
    }

    private static final ZoneId SHOP_ZONE = ZoneId.of("Europe/Belgrade");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy.");
    private static final float LEFT = 50;
    private static final float RIGHT = 545;
    private static final float TOP = 790;
    private static final float BOTTOM = 90;
    private static final float ROW = 14;
    private static final float NAME_WIDTH = 185;
    private static final float SKU_X = 245;
    private static final float SKU_WIDTH = 85;
    private static final float QUANTITY_RIGHT = 375;
    private static final float PRICE_RIGHT = 460;
    private static final float GRAY = 0.4f;

    private final byte[] regularFont = load("/fonts/Barlow-Regular.ttf");
    private final byte[] semiBoldFont = load("/fonts/Barlow-SemiBold.ttf");
    private final byte[] titleFont = load("/fonts/BarlowCondensed-Bold.ttf");

    byte[] render(Document invoice) {
        try (PDDocument pdf = new PDDocument();
                TrueTypeFont regular = parse(regularFont);
                TrueTypeFont semiBold = parse(semiBoldFont);
                TrueTypeFont title = parse(titleFont)) {
            Fonts fonts = new Fonts(
                    PDType0Font.load(pdf, regular, true),
                    PDType0Font.load(pdf, semiBold, true),
                    PDType0Font.load(pdf, title, true));
            pdf.getDocumentInformation().setTitle("Račun " + invoice.number());
            new Writer(pdf, fonts, invoice).write();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException failed) {
            throw new UncheckedIOException("Cannot render invoice " + invoice.number(), failed);
        }
    }

    // Ligatures such as "ij" are drawn as one glyph that text extraction and copy-paste cannot map back to
    // letters, so "nije" would read as "n e".
    private static TrueTypeFont parse(byte[] file) throws IOException {
        TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(file));
        font.setEnableGsub(false);
        return font;
    }

    private record Fonts(PDType0Font regular, PDType0Font semiBold, PDType0Font title) {
    }

    private static final class Writer {

        private final PDDocument pdf;
        private final Fonts fonts;
        private final Document invoice;
        private PDPageContentStream page;
        private float y;

        Writer(PDDocument pdf, Fonts fonts, Document invoice) {
            this.pdf = pdf;
            this.fonts = fonts;
            this.invoice = invoice;
        }

        void write() throws IOException {
            newPage();
            header();
            parties();
            tableHeader();
            for (Line line : invoice.lines()) {
                item(line);
            }
            totals();
            page.close();
            pageNumbers();
        }

        private void newPage() throws IOException {
            if (page != null) {
                page.close();
            }
            PDPage next = new PDPage(PDRectangle.A4);
            pdf.addPage(next);
            page = new PDPageContentStream(pdf, next);
            y = TOP;
            footer();
        }

        private void header() throws IOException {
            text(fonts.title(), 26, LEFT, y - 12, "RAČUN");
            textRight(fonts.semiBold(), 12, RIGHT, y, invoice.number());
            textRight(fonts.regular(), 10, RIGHT, y - 15,
                    "Datum izdavanja: " + DATE.format(invoice.issuedAt().atZone(SHOP_ZONE)));
            textRight(fonts.regular(), 10, RIGHT, y - 29, "Porudžbina #" + invoice.orderId());
            y -= 70;
        }

        private void parties() throws IOException {
            InvoiceProperties seller = invoice.seller();
            List<String> sellerLines = new ArrayList<>(wrap(fonts.regular(), 10, seller.sellerAddress(), 240));
            sellerLines.add("PIB: " + seller.taxId());
            sellerLines.add("MB: " + seller.registrationNumber());
            sellerLines.add("Tekući račun: " + seller.bankAccount());
            List<String> customerLines = new ArrayList<>(wrap(fonts.regular(), 10, invoice.customerAddress(), 225));
            customerLines.add(invoice.customerEmail());
            if (invoice.customerPhone() != null) {
                customerLines.add(invoice.customerPhone());
            }
            float left = party(LEFT, "PRODAVAC", seller.sellerName(), sellerLines);
            float right = party(320, "KUPAC", invoice.customerName(), customerLines);
            y = Math.min(left, right) - 24;
        }

        private float party(float x, String label, String name, List<String> lines) throws IOException {
            float at = y;
            gray(true);
            text(fonts.semiBold(), 8, x, at, label);
            gray(false);
            at -= 14;
            for (String nameLine : wrap(fonts.semiBold(), 11, name, 225)) {
                text(fonts.semiBold(), 11, x, at, nameLine);
                at -= ROW;
            }
            for (String line : lines) {
                text(fonts.regular(), 10, x, at, line);
                at -= ROW;
            }
            return at;
        }

        private void tableHeader() throws IOException {
            gray(true);
            text(fonts.semiBold(), 8, LEFT, y, "NAZIV");
            text(fonts.semiBold(), 8, SKU_X, y, "ŠIFRA");
            textRight(fonts.semiBold(), 8, QUANTITY_RIGHT, y, "KOL.");
            textRight(fonts.semiBold(), 8, PRICE_RIGHT, y, "CENA (RSD)");
            textRight(fonts.semiBold(), 8, RIGHT, y, "IZNOS (RSD)");
            gray(false);
            rule(y - 5, 0.8f);
            y -= 20;
        }

        private void item(Line line) throws IOException {
            List<String> name = wrap(fonts.regular(), 10, line.productName(), NAME_WIDTH);
            if (y - name.size() * ROW < BOTTOM + 20) {
                newPage();
                tableHeader();
            }
            float top = y;
            for (String part : name) {
                text(fonts.regular(), 10, LEFT, y, part);
                y -= ROW;
            }
            text(fonts.regular(), 10, SKU_X, top, fit(fonts.regular(), 10, line.sku(), SKU_WIDTH));
            textRight(fonts.regular(), 10, QUANTITY_RIGHT, top, String.valueOf(line.quantity()));
            textRight(fonts.regular(), 10, PRICE_RIGHT, top, RsdFormat.amount(line.unitPrice()));
            textRight(fonts.regular(), 10, RIGHT, top, RsdFormat.amount(line.lineTotal()));
            rule(y + 4, 0.3f);
            y -= 6;
        }

        private void totals() throws IOException {
            if (y - 70 < BOTTOM) {
                newPage();
            }
            y -= 8;
            String rate = invoice.vatRate().stripTrailingZeros().toPlainString();
            totalRow(fonts.regular(), 10, "Osnovica", RsdFormat.withCurrency(invoice.vat().base()));
            totalRow(fonts.regular(), 10, "PDV " + rate + "%", RsdFormat.withCurrency(invoice.vat().amount()));
            rule(y + 6, 0.8f, 340);
            y -= 6;
            totalRow(fonts.semiBold(), 12, "Ukupno za plaćanje", RsdFormat.withCurrency(invoice.total()));
        }

        private void totalRow(PDType0Font font, float size, String label, String value) throws IOException {
            text(font, size, 340, y, label);
            textRight(font, size, RIGHT, y, value);
            y -= size + 6;
        }

        private void footer() throws IOException {
            gray(true);
            text(fonts.regular(), 8, LEFT, 60,
                    "Cene sadrže PDV. Ovo nije fiskalni račun. Dokument je izdat elektronski i važi bez pečata i potpisa.");
            gray(false);
        }

        private void pageNumbers() throws IOException {
            int count = pdf.getNumberOfPages();
            for (int index = 0; index < count; index++) {
                try (PDPageContentStream stream = new PDPageContentStream(
                        pdf, pdf.getPage(index), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    page = stream;
                    gray(true);
                    textRight(fonts.regular(), 8, RIGHT, 60, "Strana " + (index + 1) + " od " + count);
                }
            }
        }

        private void text(PDType0Font font, float size, float x, float baseline, String value) throws IOException {
            page.beginText();
            page.setFont(font, size);
            page.newLineAtOffset(x, baseline);
            page.showText(drawable(font, value));
            page.endText();
        }

        private void textRight(PDType0Font font, float size, float right, float baseline, String value)
                throws IOException {
            String drawable = drawable(font, value);
            text(font, size, right - width(font, size, drawable), baseline, drawable);
        }

        private void rule(float at, float width) throws IOException {
            rule(at, width, LEFT);
        }

        private void rule(float at, float width, float from) throws IOException {
            page.setLineWidth(width);
            page.setStrokingColor(0.75f);
            page.moveTo(from, at);
            page.lineTo(RIGHT, at);
            page.stroke();
        }

        private void gray(boolean on) throws IOException {
            page.setNonStrokingColor(on ? GRAY : 0f);
        }
    }

    static List<String> wrap(PDType0Font font, float size, String value, float maxWidth) throws IOException {
        String text = drawable(font, value);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(font, size, candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            while (width(font, size, word) > maxWidth) {
                int cut = word.length() - 1;
                while (cut > 1 && width(font, size, word.substring(0, cut)) > maxWidth) {
                    cut--;
                }
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            line.append(word);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    static String fit(PDType0Font font, float size, String value, float maxWidth) throws IOException {
        String text = drawable(font, value);
        if (width(font, size, text) <= maxWidth) {
            return text;
        }
        while (text.length() > 1 && width(font, size, text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }

    static String drawable(PDType0Font font, String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)) {
                out.append(' ');
                return;
            }
            String character = Character.toString(codePoint);
            try {
                font.encode(character);
                out.append(character);
            } catch (IllegalArgumentException | IOException missingGlyph) {
                out.append('?');
            }
        });
        return out.toString();
    }

    private static float width(PDType0Font font, float size, String text) throws IOException {
        return font.getStringWidth(text) / 1000 * size;
    }

    private static byte[] load(String resource) {
        try (InputStream in = InvoicePdf.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing font " + resource);
            }
            return in.readAllBytes();
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
