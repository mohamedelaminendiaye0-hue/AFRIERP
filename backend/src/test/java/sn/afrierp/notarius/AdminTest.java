package sn.afrierp.notarius;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.securite.AuthController;

/** Espace super administrateur : comptes, verrouillage, sessions, politique et journal. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(HorlogeDeTest.class)
@Transactional
class AdminTest {

    private static final String ADMIN = "admin@notarius.sn";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ResultActions connexion(String email, String motDePasse) throws Exception {
        return mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", email, "motDePasse", motDePasse))));
    }

    private String jeton(String email, String motDePasse) throws Exception {
        String corps = connexion(email, motDePasse).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(corps).get("jeton").asText();
    }

    private String jeton(String email) throws Exception {
        return jeton(email, AuthController.MOT_DE_PASSE_DEMO);
    }

    @Test
    void lAdministrationEstReserveeAuSuperAdministrateur() throws Exception {
        mvc.perform(get("/api/admin/comptes").header("Authorization", jeton("a.ndoye@etude-ndoye.sn"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/tableau").header("Authorization", jeton(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comptesDesactives", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.motsDePasseProvisoires", greaterThanOrEqualTo(1)));
    }

    @Test
    void unCompteCreeRecoitUnMotDePasseProvisoireAChangerAvantToutAcces() throws Exception {
        String admin = jeton(ADMIN);
        String corps = mvc.perform(post("/api/admin/comptes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prenom\":\"Binta\",\"nom\":\"Sall\",\"email\":\"b.sall@etude-ndoye.sn\",\"role\":\"FORMALISTE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compte.initiales", is("BS")))
                .andExpect(jsonPath("$.compte.doitChangerMotDePasse", is(true)))
                .andReturn().getResponse().getContentAsString();
        String provisoire = json.readTree(corps).get("motDePasseProvisoire").asText();

        String session = connexion("b.sall@etude-ndoye.sn", provisoire).andExpect(jsonPath("$.doitChangerMotDePasse", is(true)))
                .andReturn().getResponse().getContentAsString();
        String jetonProvisoire = "Bearer " + json.readTree(session).get("jeton").asText();
        mvc.perform(get("/api/dossiers").header("Authorization", jetonProvisoire))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.blocages[0].code", is("MOT_DE_PASSE_A_CHANGER")));

        mvc.perform(post("/api/auth/mot-de-passe").header("Authorization", jetonProvisoire).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("actuel", provisoire, "nouveau", "court1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blocages[0].code", is("MOT_DE_PASSE_COURT")));
        String neuve = mvc.perform(post("/api/auth/mot-de-passe").header("Authorization", jetonProvisoire).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("actuel", provisoire, "nouveau", "Teranga2026dakar"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doitChangerMotDePasse", is(false)))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/dossiers").header("Authorization", "Bearer " + json.readTree(neuve).get("jeton").asText()))
                .andExpect(status().isOk());
        // L'ancienne session (mot de passe provisoire) est fermée.
        mvc.perform(get("/api/moi").header("Authorization", jetonProvisoire)).andExpect(status().isUnauthorized());
    }

    @Test
    void lesReglesDeContinuiteDeLEtudeSontAppliquees() throws Exception {
        String admin = jeton(ADMIN);
        mvc.perform(post("/api/admin/comptes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prenom\":\"Moustapha\",\"nom\":\"Fall\",\"email\":\"m.fall@etude-ndoye.sn\",\"role\":\"NOTAIRE_TITULAIRE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blocages[*].code", hasItem("TITULAIRE_UNIQUE")));
        mvc.perform(post("/api/admin/comptes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prenom\":\"Lamine\",\"nom\":\"Sy\",\"email\":\"a.diallo@etude-ndoye.sn\",\"role\":\"NOTAIRE_SALARIE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blocages[*].code", hasItem("EMAIL_UTILISE")))
                .andExpect(jsonPath("$.blocages[*].code", hasItem("RESPONSABLE_REQUIS")));
        mvc.perform(post("/api/admin/comptes/U-003/desactivation").header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blocages[0].code", is("DOSSIERS_EN_COURS")));
        mvc.perform(post("/api/admin/comptes/U-099/desactivation").header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blocages[*].code", hasItem("SOI_MEME")));
        // Un compte sans dossier se désactive : ses sessions sont fermées et il ne peut plus se connecter.
        String nouvelle = jeton("n.ba@etude-ndoye.sn");
        mvc.perform(post("/api/admin/comptes/U-009/desactivation").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif", is(false)));
        mvc.perform(get("/api/moi").header("Authorization", nouvelle)).andExpect(status().isUnauthorized());
        connexion("n.ba@etude-ndoye.sn", AuthController.MOT_DE_PASSE_DEMO).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", startsWith("Ce compte est désactivé")));
    }

    @Test
    void verrouillageApresEchecsRepetesPuisDeverrouillageParLAdministrateur() throws Exception {
        for (int i = 0; i < 5; i++) {
            connexion("m.tall@etude-ndoye.sn", "mauvais").andExpect(status().isUnauthorized());
        }
        connexion("m.tall@etude-ndoye.sn", AuthController.MOT_DE_PASSE_DEMO).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", startsWith("Compte verrouillé")));
        String admin = jeton(ADMIN);
        mvc.perform(get("/api/admin/journal?type=COMPTE_VERROUILLE").header("Authorization", admin))
                .andExpect(jsonPath("$[0].cibleId", is("U-008")));
        mvc.perform(post("/api/admin/comptes/U-008/deverrouillage").header("Authorization", admin))
                .andExpect(jsonPath("$.verrouille", is(false)));
        connexion("m.tall@etude-ndoye.sn", AuthController.MOT_DE_PASSE_DEMO).andExpect(status().isOk());
    }

    @Test
    void reinitialisationEtRevocationFermentLesSessions() throws Exception {
        String admin = jeton(ADMIN);
        String clerc = jeton("i.sow@etude-ndoye.sn");
        mvc.perform(post("/api/admin/comptes/U-004/revocation").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/moi").header("Authorization", clerc)).andExpect(status().isUnauthorized());

        String corps = mvc.perform(post("/api/admin/comptes/U-004/reinitialisation").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode r = json.readTree(corps);
        connexion("i.sow@etude-ndoye.sn", AuthController.MOT_DE_PASSE_DEMO).andExpect(status().isUnauthorized());
        connexion("i.sow@etude-ndoye.sn", r.get("motDePasseProvisoire").asText()).andExpect(status().isOk())
                .andExpect(jsonPath("$.doitChangerMotDePasse", is(true)));
    }

    @Test
    void laPolitiqueDeSecuriteEstParametrableEtTracee() throws Exception {
        String admin = jeton(ADMIN);
        mvc.perform(put("/api/admin/parametres").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longueurMinimale\":4,\"dureeSessionHeures\":8,\"tentativesAvantVerrouillage\":3,\"dureeVerrouillageMinutes\":30,\"changementObligatoire\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/parametres").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longueurMinimale\":12,\"dureeSessionHeures\":8,\"tentativesAvantVerrouillage\":3,\"dureeVerrouillageMinutes\":30,\"changementObligatoire\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tentativesAvantVerrouillage", is(3)));
        mvc.perform(get("/api/admin/journal?q=12 car").header("Authorization", admin))
                .andExpect(jsonPath("$[0].type", is("PARAMETRES_MODIFIES")));
    }
}
