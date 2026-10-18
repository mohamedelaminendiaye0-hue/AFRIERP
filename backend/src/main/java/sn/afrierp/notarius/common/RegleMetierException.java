package sn.afrierp.notarius.common;

import java.util.List;

/** Operation refusee par une regle metier (HTTP 409). Porte la liste exhaustive des blocages. */
public class RegleMetierException extends RuntimeException {

    private final transient List<Blocage> blocages;

    public RegleMetierException(List<Blocage> blocages) {
        super(blocages.stream().map(Blocage::message).reduce((a, b) -> a + " " + b).orElse("Opération refusée."));
        this.blocages = List.copyOf(blocages);
    }

    public RegleMetierException(String code, String message) {
        this(List.of(new Blocage(code, message)));
    }

    public List<Blocage> getBlocages() {
        return blocages;
    }
}
