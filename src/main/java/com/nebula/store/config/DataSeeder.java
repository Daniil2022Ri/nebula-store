package com.nebula.store.config;

import com.nebula.store.domain.Product;
import com.nebula.store.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fills an empty catalog with demo products so the storefront is never blank.
 *
 * <p>Seeding is skipped when the table already contains rows, so restarts against the
 * file-backed H2 database keep the data intact. Disable it with
 * {@code app.seed-demo-data=false} or {@code NEBULA_SEED=false}.</p>
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ProductRepository products;
    private final boolean enabled;

    public DataSeeder(ProductRepository products,
                      @Value("${app.seed-demo-data:true}") boolean enabled) {
        this.products = products;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Demo data seeding is disabled");
            return;
        }
        long existing = products.count();
        if (existing > 0) {
            log.info("Catalog already contains {} products, skipping seed", existing);
            return;
        }
        List<Product> demoCatalog = catalog();
        products.saveAll(demoCatalog);
        log.info("Seeded {} demo products", demoCatalog.size());
    }

    static List<Product> catalog() {
        return List.of(
                product("aurora-x5", "Наушники Aurora X5",
                        "Беспроводные накладные наушники с гибридным шумоподавлением,"
                                + " кодеком LDAC и автономностью до 40 часов.",
                        "Аудио", "12990.00", 14, 4.8, "\uD83C\uDFA7"),
                product("nova-mini", "Колонка Nova Mini",
                        "Портативная Bluetooth-колонка с влагозащитой IP67,"
                                + " пассивным сабвуфером и 20 часами работы.",
                        "Аудио", "5490.00", 32, 4.6, "\uD83D\uDD0A"),
                product("echo-studio", "Микрофон Echo Studio",
                        "USB-C конденсаторный микрофон для стримов и подкастов"
                                + " с кардиоидной диаграммой и мониторингом в реальном времени.",
                        "Аудио", "11990.00", 9, 4.7, "\uD83C\uDF99"),
                product("flux-75", "Клавиатура Flux 75",
                        "Механическая клавиатура формата 75% с hot-swap переключателями,"
                                + " алюминиевым корпусом и тройным подключением.",
                        "Клавиатуры", "8990.00", 18, 4.9, "\u2328"),
                product("pulse-tkl", "Клавиатура Pulse TKL",
                        "Компактная TKL-клавиатура с gasket-mount, шумоизоляцией"
                                + " и предсмазанными линейными переключателями.",
                        "Клавиатуры", "6490.00", 21, 4.5, "\u2328"),
                product("vega-27", "Монитор Vega 27 4K",
                        "27-дюймовый IPS-монитор 4K с частотой 144 Гц,"
                                + " покрытием 98% DCI-P3 и USB-C с зарядкой 90 Вт.",
                        "Мониторы", "44990.00", 6, 4.8, "\uD83D\uDDA5"),
                product("orbit-s2", "Смарт-часы Orbit S2",
                        "AMOLED-экран, GPS, ЭКГ и мониторинг сна."
                                + " До 14 дней автономности в режиме экономии.",
                        "Носимые", "19990.00", 12, 4.4, "\u231A"),
                product("halo-ring", "Умное кольцо Halo",
                        "Титановое кольцо с пульсометром, датчиком температуры"
                                + " и оценкой готовности к нагрузкам.",
                        "Носимые", "24990.00", 7, 4.3, "\uD83D\uDC8D"),
                product("pulse-cable", "Кабель Pulse USB-C 2 м",
                        "Оплёточный кабель 240 Вт с поддержкой DisplayPort Alt Mode"
                                + " и передачей данных 40 Гбит/с.",
                        "Аксессуары", "1490.00", 120, 4.2, "\uD83D\uDD0C"),
                product("nebula-hub", "Док Nebula Hub 8-в-1",
                        "USB-C хаб: HDMI 4K60, два USB-A, Ethernet, SD/microSD"
                                + " и сквозная зарядка на 100 Вт.",
                        "Аксессуары", "6490.00", 25, 4.6, "\uD83E\uDDE9"),
                product("flux-mat", "Коврик Flux XXL",
                        "Тканевый коврик 900x400 мм с водонепроницаемым покрытием"
                                + " и прошитым краем.",
                        "Аксессуары", "2490.00", 60, 4.5, "\uD83D\uDFEA"),
                product("iris-cam", "Веб-камера Iris 2K",
                        "Камера 2K с автофокусом, HDR и встроенным микрофоном"
                                + " с шумоподавлением для созвонов и стримов.",
                        "Аксессуары", "7990.00", 15, 4.1, "\uD83D\uDCF7")
        );
    }

    private static Product product(String slug, String name, String description, String category,
                                   String price, int stock, double rating, String icon) {
        return new Product(slug, name, description, category, new BigDecimal(price), stock, rating, icon);
    }
}
