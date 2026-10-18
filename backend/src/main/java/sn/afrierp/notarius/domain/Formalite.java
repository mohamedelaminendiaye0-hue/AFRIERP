package sn.afrierp.notarius.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Formalite post-acte suivie par le formaliste : deposee, puis achevee par l'administration. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Formalite {

    @Enumerated(EnumType.STRING)
    private OrganismeFormalites organisme;

    private LocalDate dateDepot;
    private String reference;
    /** Date a laquelle l'administration a acheve la formalite (mutation faite, inscription, parution). */
    private LocalDate dateAchevement;

    public boolean estAchevee() {
        return dateAchevement != null;
    }
}
