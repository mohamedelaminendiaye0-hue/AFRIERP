package sn.afrierp.notarius.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Bien {

    private String designation;
    /** Numero de titre foncier (ex : "TF n° 12 345/DG"). */
    private String titreFoncier;
    private String adresse;
    private String commune;
    private Integer superficieM2;

    @Enumerated(EnumType.STRING)
    private UsageBien usage;
}
