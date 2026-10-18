package sn.afrierp.notarius;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.repository.ClientRepository;
import sn.afrierp.notarius.securite.AuthController;

/** Contrat HTTP : authentification, fichier clients, repertoire, minutier et marque blanche. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(HorlogeDeTest.class)
@Transactional
class ApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClientRepository clients;

    private String jeton(String email) throws Exception {
        String corps = mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"motDePasse\":\"%s\"}".formatted(email, AuthController.MOT_DE_PASSE_DEMO)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(corps).get("jeton").asText();
    }

    @Test
    void sansJetonLApiEstFermeeMaisLIdentiteResteLisible() throws Exception {
        mvc.perform(get("/api/dossiers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/identite")).andExpect(status().isOk()).andExpect(jsonPath("$.couleurPrincipale").exists());
        mvc.perform(get("/api/auth/comptes-demo")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(13)));
    }

    @Test
    void connexionRefuseUnMauvaisMotDePasseEtOuvreLaSessionSinon() throws Exception {
        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a.ndoye@etude-ndoye.sn\",\"motDePasse\":\"faux\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/moi").header("Authorization", jeton("A.NDOYE@etude-ndoye.sn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("U-001")))
                .andExpect(jsonPath("$.role", is("NOTAIRE_TITULAIRE")));
        mvc.perform(get("/api/moi").header("Authorization", "Bearer jeton.falsifie.xyz")).andExpect(status().isUnauthorized());
    }

    @Test
    void laFicheClientRetraceTousSesDossiersQuelQueSoitSonRole() throws Exception {
        String id = clients.findFirstByIdentifiantIgnoreCase("CNI 1 234 1991 07731").orElseThrow().getId();
        mvc.perform(get("/api/clients/" + id).header("Authorization", jeton("i.sow@etude-ndoye.sn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.nom", is("Mme Khady Gueye")))
                .andExpect(jsonPath("$.nbDossiers", is(3)))
                .andExpect(jsonPath("$.dossiers[*].dossierId", hasItem("D-0142")))
                .andExpect(jsonPath("$.dossiers[*].dossierId", hasItem("D-0082")))
                .andExpect(jsonPath("$.dossiers[*].qualite", everyItem(is("ACQUEREUR"))));
        mvc.perform(get("/api/clients?q=ndiaye").header("Authorization", jeton("n.ba@etude-ndoye.sn")))
                .andExpect(jsonPath("$[?(@.nom == 'M. Abdoulaye Ndiaye')].nbDossiers", hasItem(3)));
    }

    @Test
    void leRepertoireNumeroteLesActesSignesDansLOrdreChronologique() throws Exception {
        String corps = mvc.perform(get("/api/repertoire?annee=2026").header("Authorization", jeton("a.ndoye@etude-ndoye.sn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes", hasSize(9)))
                .andExpect(jsonPath("$.trimestres", hasSize(greaterThanOrEqualTo(1))))
                .andReturn().getResponse().getContentAsString();
        JsonNode lignes = json.readTree(corps).get("lignes");
        // Liste antechronologique : numeros decroissants, dates de signature decroissantes.
        for (int i = 1; i < lignes.size(); i++) {
            assert lignes.get(i - 1).get("numero").asText().compareTo(lignes.get(i).get("numero").asText()) > 0;
            assert lignes.get(i - 1).get("date").asText().compareTo(lignes.get(i).get("date").asText()) >= 0;
        }
    }

    @Test
    void lArchivageGroupeDetailleLesRefusDossierParDossier() throws Exception {
        mvc.perform(post("/api/minutier/archivage").header("Authorization", jeton("s.diouf@etude-ndoye.sn"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dossierIds\":[\"D-0125\",\"D-0076\",\"D-0082\"],\"emplacement\":\"Liasse 2026-T3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].archive", is(false)))
                .andExpect(jsonPath("$[0].blocages", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[1].archive", is(false)))
                // Formalités achevées, taxage arrêté, reliquat restitué : la minute est archivée et le dossier clôturé.
                .andExpect(jsonPath("$[2].archive", is(true)));
        mvc.perform(get("/api/dossiers/D-0082").header("Authorization", jeton("s.diouf@etude-ndoye.sn")))
                .andExpect(jsonPath("$.resume.statut", is("CLOSED")));
    }

    @Test
    void laMarqueBlancheEstReserveeAuSuperAdministrateur() throws Exception {
        String saisie = """
                {"nom":"Etude Diallo & Associes","sousTitre":"Saint-Louis","couleurPrincipale":"#123b2f",
                 "couleurAccent":"#D4A373","police":"IBM Plex Sans"}""";
        mvc.perform(put("/api/identite").header("Authorization", jeton("a.ndoye@etude-ndoye.sn"))
                        .contentType(MediaType.APPLICATION_JSON).content(saisie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blocages[0].code", is("ROLE_NON_HABILITE")));
        mvc.perform(put("/api/identite").header("Authorization", jeton("admin@notarius.sn"))
                        .contentType(MediaType.APPLICATION_JSON).content(saisie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couleurPrincipale", is("#123B2F")))
                .andExpect(jsonPath("$.monogramme", is("E")));
        mvc.perform(get("/api/identite")).andExpect(jsonPath("$.nom", is("Etude Diallo & Associes")));
    }
}
