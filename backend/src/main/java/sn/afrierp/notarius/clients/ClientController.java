package sn.afrierp.notarius.clients;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.afrierp.notarius.domain.Client;
import sn.afrierp.notarius.domain.TypePersonne;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.web.UtilisateurCourant;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    public record SaisieClient(@NotNull TypePersonne type, @NotBlank String nom, String identifiant, String telephone,
                               String email, String adresse, String regimeMatrimonial, String notes) {

        ClientService.DonneesClient donnees() {
            return new ClientService.DonneesClient(type, nom, identifiant, telephone, email, adresse, regimeMatrimonial, notes);
        }
    }

    @GetMapping
    public List<ClientService.ClientResume> lister(@RequestParam(required = false) String q) {
        return service.lister(q);
    }

    @GetMapping("/{id}")
    public ClientService.FicheClient fiche(@PathVariable String id) {
        return service.fiche(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Client creer(@Valid @RequestBody SaisieClient saisie, @UtilisateurCourant Utilisateur moi) {
        return service.creer(saisie.donnees(), moi);
    }

    @PutMapping("/{id}")
    public Client modifier(@PathVariable String id, @Valid @RequestBody SaisieClient saisie, @UtilisateurCourant Utilisateur moi) {
        return service.modifier(id, saisie.donnees(), moi);
    }
}
