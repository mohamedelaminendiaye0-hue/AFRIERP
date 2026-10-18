package sn.afrierp.notarius;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.securite.AuthController;

/** Répertoire des actes : état d'archivage et preuves jointes. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(HorlogeDeTest.class)
@Transactional
class PreuvesTest {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String jeton(String email) throws Exception {
        String corps = mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "motDePasse", AuthController.MOT_DE_PASSE_DEMO))))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(corps).get("jeton").asText();
    }

    @Test
    void leRepertoireTientLieuDArchivesEtCompteLesPreuves() throws Exception {
        mvc.perform(get("/api/repertoire?annee=2026").header("Authorization", jeton("s.diouf@etude-ndoye.sn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[?(@.dossierId == 'D-0076')].etat", hasItem("ARCHIVEE")))
                .andExpect(jsonPath("$.lignes[?(@.dossierId == 'D-0082')].etat", hasItem("A_ARCHIVER")))
                .andExpect(jsonPath("$.lignes[?(@.dossierId == 'D-0076')].nbPreuves", hasItem(2)))
                .andExpect(jsonPath("$.lignes[?(@.dossierId == 'D-0125')].nbPreuves", hasItem(1)));
    }

    @Test
    void unePreuveVerseeEstConsultableAvecSonEmpreinte() throws Exception {
        String archiviste = jeton("s.diouf@etude-ndoye.sn");
        String corps = mvc.perform(multipart("/api/repertoire/D-0125/preuves")
                        .file(new MockMultipartFile("fichier", "minute D-0125.pdf", "application/pdf", PDF))
                        .param("type", "MINUTE_SIGNEE").param("commentaire", "Rescan haute définition")
                        .header("Authorization", archiviste))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeContenu", is("application/pdf")))
                .andExpect(jsonPath("$.empreinteSha256").isString())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(corps).get("id").asLong();
        String empreinte = json.readTree(corps).get("empreinteSha256").asText();

        mvc.perform(get("/api/repertoire/D-0125/preuves").header("Authorization", jeton("a.ndoye@etude-ndoye.sn")))
                .andExpect(jsonPath("$[0].id", is((int) id)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(2)));
        mvc.perform(get("/api/repertoire/preuves/" + id + "/contenu").header("Authorization", archiviste))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Empreinte-Sha256", empreinte))
                .andExpect(content().bytes(PDF));
        mvc.perform(get("/api/dossiers/D-0125").header("Authorization", archiviste))
                .andExpect(jsonPath("$.journal[*].type", hasItem("PREUVE_VERSEE")));
    }

    @Test
    void lesPreuvesSontControlees() throws Exception {
        String archiviste = jeton("s.diouf@etude-ndoye.sn");
        // Un faux PDF (contenu texte) est refusé : le format est lu dans les octets, pas dans le nom.
        mvc.perform(multipart("/api/repertoire/D-0125/preuves")
                        .file(new MockMultipartFile("fichier", "faux.pdf", "application/pdf", "bonjour".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", archiviste))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blocages[0].code", is("FORMAT_REFUSE")));
        // Un dossier non signé n'est pas au répertoire.
        mvc.perform(multipart("/api/repertoire/D-0142/preuves").file(new MockMultipartFile("fichier", "x.pdf", "application/pdf", PDF))
                        .header("Authorization", archiviste))
                .andExpect(jsonPath("$.blocages[0].code", is("ACTE_NON_INSCRIT")));
        // Le clerc rédacteur consulte mais ne verse pas.
        mvc.perform(multipart("/api/repertoire/D-0125/preuves").file(new MockMultipartFile("fichier", "x.pdf", "application/pdf", PDF))
                        .header("Authorization", jeton("i.sow@etude-ndoye.sn")))
                .andExpect(jsonPath("$.blocages[0].code", is("ROLE_NON_HABILITE")));
    }
}
