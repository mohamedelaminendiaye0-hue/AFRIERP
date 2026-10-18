package sn.afrierp.notarius;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import sn.afrierp.notarius.securite.AuthController;

/**
 * Sans transaction englobante : chaque requête valide (ou non) sa propre
 * transaction, comme en production. Vérifie qu'un changement de mot de passe
 * est réellement enregistré et que la nouvelle session est acceptée.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(HorlogeDeTest.class)
class SessionReelleTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String connexion(String email, String motDePasse) throws Exception {
        String corps = mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "motDePasse", motDePasse))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(corps).get("jeton").asText();
    }

    @Test
    void leChangementDeMotDePasseEstEnregistreEtLaNouvelleSessionAcceptee() throws Exception {
        String admin = connexion("admin@notarius.sn", AuthController.MOT_DE_PASSE_DEMO);
        String cree = mvc.perform(post("/api/admin/comptes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prenom\":\"Rama\",\"nom\":\"Thiam\",\"email\":\"r.thiam@etude-ndoye.sn\",\"role\":\"ASSISTANT\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(cree).at("/compte/id").asText();
        String provisoire = json.readTree(cree).get("motDePasseProvisoire").asText();

        String jetonProvisoire = connexion("r.thiam@etude-ndoye.sn", provisoire);
        String session = mvc.perform(post("/api/auth/mot-de-passe").header("Authorization", jetonProvisoire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("actuel", provisoire, "nouveau", "Accueil2026plateau"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/moi").header("Authorization", "Bearer " + json.readTree(session).get("jeton").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id", is(id)));
        connexion("r.thiam@etude-ndoye.sn", "Accueil2026plateau");
        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "r.thiam@etude-ndoye.sn", "motDePasse", provisoire))))
                .andExpect(status().isUnauthorized());

        // Nettoyage : le compte de test est désactivé (les comptes ne se suppriment pas).
        mvc.perform(post("/api/admin/comptes/" + id + "/desactivation").header("Authorization", admin)).andExpect(status().isOk());
    }
}
