package sn.afrierp.notarius;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Date figee : les tests sont reproductibles quel que soit le jour d'execution. */
@TestConfiguration
public class HorlogeDeTest {

    public static final LocalDate AUJOURDHUI = LocalDate.of(2026, 9, 16);

    @Bean
    @Primary
    public Clock horlogeFixe() {
        ZoneId zone = ZoneId.of("Africa/Dakar");
        return Clock.fixed(AUJOURDHUI.atTime(9, 0).atZone(zone).toInstant(), zone);
    }
}
