# Nebula Store

[![CI](https://github.com/Daniil2022Ri/nebula-store/actions/workflows/ci.yml/badge.svg)](https://github.com/Daniil2022Ri/nebula-store/actions/workflows/ci.yml)

Демо-интернет-магазин электроники: REST API на **Java 21 + Spring Boot 3.5** и витрина на
чистом HTML/CSS/JS без сборщика.

![Витрина Nebula Store](docs/storefront.png)

> **Статус: этап 2 — каталог + корзина.**
> Готовы доменная модель, REST API каталога, витрина и серверная корзина.
> Оформление заказа и админка — следующие шаги, они перечислены в
> [дорожной карте](#дорожная-карта).

## Что уже работает

- **Каталог** из 12 демо-товаров в пяти категориях; данные лежат в H2 и засеиваются при первом старте.
- **Поиск** по названию, описанию и категории — без учёта регистра и с дебаунсом в UI.
- **Фильтр по категориям** и пять режимов сортировки: популярные, дешевле, дороже, по рейтингу, по названию.
- **Корзина** на сервере: браузер хранит только идентификатор, цены и остатки
  пересчитываются по каталогу при каждом запросе, количество ограничено складом.
- **REST API** с валидацией и ошибками в формате RFC 7807 (`application/problem+json`).
- **Витрина**: hero-блок со статистикой, чипсы категорий, адаптивная сетка карточек,
  скелетоны загрузки, пустое состояние, модалка товара с диплинком `#product-<id>`,
  шторка корзины со степперами, бейджем и итоговой суммой, тосты.
- **Тесты**: 19 штук — интеграционные на MockMvc и юнит-тесты сидера.
- **CI**: сборка и тесты на GitHub Actions при каждом пуше.

Корзина — шторка справа с пересчётом суммы и ограничением по остаткам:

![Корзина Nebula Store](docs/cart.png)

## Стек

| Слой          | Технологии                                                        |
|---------------|-------------------------------------------------------------------|
| Язык          | Java 21 (проект собирается и на JDK 25)                            |
| Бэкенд        | Spring Boot 3.5 — Web MVC, Data JPA, Validation, Actuator          |
| БД            | H2 2.x, файловый режим `./data/nebula`                              |
| Фронтенд      | Vanilla JS (ES2022), CSS custom properties, `<dialog>`, без npm     |
| Сборка        | Maven 3.9 + Maven Wrapper                                          |
| Тесты         | JUnit 5, AssertJ, MockMvc, Hamcrest                                |

## Быстрый старт

Нужен JDK 21 или новее.

```bash
git clone https://github.com/Daniil2022Ri/nebula-store.git
cd nebula-store
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

| Что                | Адрес                                      |
|--------------------|--------------------------------------------|
| Витрина            | <http://localhost:8080>                    |
| API                | <http://localhost:8080/api/products>       |
| Health             | <http://localhost:8080/actuator/health>    |
| Консоль H2         | <http://localhost:8080/h2-console>         |

Параметры консоли H2: JDBC URL `jdbc:h2:file:./data/nebula`, пользователь `sa`, пароль пустой.

Собрать исполняемый jar и запустить его:

```bash
./mvnw clean package
java -jar target/nebula-store-1.0.0.jar
```

Чтобы начать с чистого каталога, остановите приложение и удалите папку `data/`.

## API

| Метод | Путь                | Параметры                                             | Описание                        |
|-------|---------------------|-------------------------------------------------------|---------------------------------|
| GET   | `/api/products`     | `category`, `q`, `sort` (`popular` по умолчанию)      | Список активных товаров         |
| GET   | `/api/products/{id}`| —                                                     | Один товар или `404`            |
| GET   | `/api/categories`   | —                                                     | Категории с количеством товаров |
| GET   | `/api/cart`         | заголовок `X-Cart-Id`                                 | Корзина (создаётся пустой)      |
| POST  | `/api/cart/items`   | `{ "productId": 1, "quantity": 2 }`                   | Добавить товар к текущему количеству |
| PUT   | `/api/cart/items/{productId}` | `{ "quantity": 3 }`                          | Задать количество (`0` — удалить) |
| DELETE| `/api/cart/items/{productId}` | —                                            | Удалить позицию                 |
| DELETE| `/api/cart`         | —                                                     | Очистить корзину                |

Корзина идентифицируется непрозрачным id: клиент присылает его в заголовке `X-Cart-Id`,
а если заголовка нет — сервер создаёт новую корзину и возвращает её `id` в теле ответа.
Каждый вызов возвращает полный снимок корзины, поэтому UI просто перерисовывает ответ.

Примеры:

```bash
curl "http://localhost:8080/api/products?q=клавиатура"
curl "http://localhost:8080/api/products?category=Аудио&sort=price_desc"
curl "http://localhost:8080/api/categories"

CART=$(curl -s http://localhost:8080/api/cart | jq -r .id)
curl -s -X POST http://localhost:8080/api/cart/items \
     -H "X-Cart-Id: $CART" -H "Content-Type: application/json" \
     -d '{"productId":1,"quantity":2}'
curl -s -X PUT http://localhost:8080/api/cart/items/1 \
     -H "X-Cart-Id: $CART" -H "Content-Type: application/json" -d '{"quantity":5}'
```

Ответ `GET /api/cart`:

```json
{
  "id": "6a4d1f5e-7c1e-4a52-9a4e-3c0f0d2b9a11",
  "items": [
    {
      "productId": 1,
      "slug": "aurora-x5",
      "name": "Наушники Aurora X5",
      "category": "Аудио",
      "icon": "🎧",
      "unitPrice": 12990.00,
      "quantity": 2,
      "lineTotal": 25980.00,
      "stock": 14,
      "inStock": true
    }
  ],
  "itemCount": 2,
  "subtotal": 25980.00
}
```

Ответ `GET /api/products/{id}`:

```json
{
  "id": 1,
  "slug": "aurora-x5",
  "name": "Наушники Aurora X5",
  "description": "Беспроводные накладные наушники с гибридным шумоподавлением…",
  "category": "Аудио",
  "price": 12990.00,
  "stock": 14,
  "rating": 4.8,
  "icon": "🎧",
  "inStock": true
}
```

Ошибка `GET /api/products/999999`:

```json
{
  "type": "https://nebula.store/errors/not-found",
  "title": "Не найдено",
  "status": 404,
  "detail": "Товар с id=999999 не найден"
}
```

## Структура проекта

```
src/main/java/com/nebula/store
├── StoreApplication.java        точка входа
├── config/DataSeeder.java       демо-каталог при первом старте
├── domain/Product.java          JPA-сущность товара
├── domain/Cart.java             корзина в памяти: id и количества
├── repository/                  Spring Data репозиторий
├── service/ProductService.java  поиск, фильтрация, сортировка
├── service/CartService.java     корзина: TTL, лимиты, снимки для UI
├── web/                         REST-контроллеры: products, categories, cart
│   └── dto/                     ProductDto, CategoryDto, CartDto, CartLineDto, запросы
└── error/                       NotFoundException + RFC 7807 handler

src/main/resources
├── application.yml
└── static/                      витрина: index.html, css/, js/, favicon.svg

src/test/                        интеграционные (MockMvc) и юнит-тесты
```

## Конфигурация

| Переменная / свойство        | По умолчанию            | Назначение                          |
|------------------------------|-------------------------|-------------------------------------|
| `server.port`                | `8080`                  | Порт приложения                     |
| `spring.datasource.url`      | `jdbc:h2:file:./data/nebula` | Подключение к H2               |
| `NEBULA_SEED` / `app.seed-demo-data` | `true`          | Засеивать ли демо-каталог           |
| `app.cart-ttl-minutes`       | `720`                   | Сколько минут живёт брошенная корзина |
| `NEBULA_ADMIN_TOKEN` / `app.admin-token` | `dev-admin-token` | Токен админки (появится на этапе 4) |

## Тесты

```bash
./mvnw test        # только тесты
./mvnw verify      # сборка + тесты
```

Покрыто: выдача каталога, фильтр по категории, регистронезависимый поиск,
сортировка по цене, категории со счётчиками, `404`/`400` в формате problem detail,
консистентность демо-данных, добавление в корзину, инкремент позиции, лимит по складу,
изменение и обнуление количества, удаление позиции и очистка корзины, изоляция корзин.

## Дорожная карта

- [x] Этап 1 — домен товаров, каталог, REST API, витрина, тесты, CI
- [x] Этап 2 — корзина: `GET/POST/PUT/DELETE /api/cart`, шторка, бейдж, лимиты по складу
- [ ] Этап 3 — оформление заказа: `POST /api/orders`, снимок цен, списание стока, страница успеха
- [ ] Этап 4 — админка заказов по токену, смена статусов
- [ ] Этап 5 — Dockerfile + PostgreSQL, миграции Flyway
