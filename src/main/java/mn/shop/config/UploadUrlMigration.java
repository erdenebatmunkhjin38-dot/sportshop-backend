package mn.shop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Өмнө нь зургийг "http://localhost:3000/uploads/<uuid>.jpg" гэж бүтэн хаягаар хадгалдаг байсан.
 * Утсан дээр localhost ажиллахгүй тул сервер асах бүрт "/uploads/<uuid>.jpg" болгож засна.
 * Зөвхөн админы оруулсан (UUID нэртэй) зургийг өөрчилнө, гадны холбоосод хүрэхгүй.
 */
@Component
public class UploadUrlMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UploadUrlMigration.class);

    // Хүснэгт ба зургийн багана
    private static final String[][] COLUMNS = {
            {"product", "image_url"},
            {"product_variant", "image_url"},
            {"brand", "logo_url"},
            {"branch", "image_url"},
            {"post", "image_url"},
    };
    private static final String UPLOADED =
            "'^https?://[^/]+/uploads/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\\\.(jpg|png|webp|gif)$'";

    private final JdbcTemplate jdbc;

    public UploadUrlMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String[] c : COLUMNS) {
            String table = c[0], column = c[1];
            int changed = jdbc.update("UPDATE " + table + " SET " + column + " = SUBSTRING(" + column
                    + ", LOCATE('/uploads/', " + column + ")) WHERE " + column + " REGEXP " + UPLOADED);
            if (changed > 0) log.info("{}.{}: {} зургийн хаягийг /uploads/ болголоо", table, column, changed);
        }
    }
}
