package sn.afrierp.notarius.config;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Génère un PDF d'une page (texte seul) pour les preuves du jeu de démonstration :
 * minute numérisée, quittance d'enregistrement. Aucun usage en production.
 */
final class PdfDemo {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private PdfDemo() {
    }

    /** @param titre première ligne, en gras ; @param lignes corps du document */
    static byte[] page(String titre, List<String> lignes) {
        StringBuilder flux = new StringBuilder("BT\n/F2 16 Tf\n60 780 Td\n(").append(echapper(titre)).append(") Tj\n/F1 11 Tf\n");
        flux.append("0 -14 Td\n");
        for (String l : lignes) {
            flux.append("0 -18 Td\n(").append(echapper(l)).append(") Tj\n");
        }
        flux.append("ET\n0.6 w 60 60 m 535 60 l S\nBT /F1 8 Tf 60 46 Td (Document de démonstration Notarius - sans valeur juridique) Tj ET\n");
        byte[] contenu = flux.toString().getBytes(WINDOWS_1252);

        List<String> objets = new ArrayList<>();
        objets.add("<< /Type /Catalog /Pages 2 0 R >>");
        objets.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objets.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R "
                + "/Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>");
        objets.add(null); // flux de contenu, écrit en binaire
        objets.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objets.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ecrire(sortie, "%PDF-1.4\n");
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < objets.size(); i++) {
            positions.add(sortie.size());
            ecrire(sortie, (i + 1) + " 0 obj\n");
            if (objets.get(i) == null) {
                ecrire(sortie, "<< /Length " + contenu.length + " >>\nstream\n");
                sortie.writeBytes(contenu);
                ecrire(sortie, "\nendstream\n");
            } else {
                ecrire(sortie, objets.get(i) + "\n");
            }
            ecrire(sortie, "endobj\n");
        }
        int xref = sortie.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objets.size() + 1) + "\n0000000000 65535 f \n");
        positions.forEach(p -> table.append("%010d 00000 n \n".formatted(p)));
        table.append("trailer\n<< /Size ").append(objets.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        ecrire(sortie, table.toString());
        return sortie.toByteArray();
    }

    private static String echapper(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static void ecrire(ByteArrayOutputStream sortie, String texte) {
        sortie.writeBytes(texte.getBytes(StandardCharsets.ISO_8859_1));
    }
}
