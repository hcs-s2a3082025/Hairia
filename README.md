# Hairia

Gradle / Spring Boot 3 のWebアプリケーションです。画面は `src/main/resources/static`、
PostgreSQLの初期スキーマはFlywayマイグレーションで管理します。

## 必要な環境

- Java 21以上
- Docker Compose
- Gradle Wrapper（`./gradlew`）

## ローカル起動

```sh
docker compose up -d db
./gradlew bootRun
```

ブラウザーで http://localhost:8080 を開きます。アプリ起動時に
`src/main/resources/db/migration` の未適用マイグレーションが実行されます。
ローカルDBの接続先は `application.properties` の既定値です。変更する場合は
`DATABASE_URL`、`DATABASE_USERNAME`、`DATABASE_PASSWORD` 環境変数を設定してください。
Composeの既定パスワード（`hairia_dev`）はローカル開発専用です。本番環境では必ず変更してください。

## プロジェクト構成

```text
.
├── build.gradle
├── settings.gradle
├── docker-compose.yml
└── src
    └── main
        ├── java/jp/hairia
        │   ├── HairiaApplication.java
        │   ├── common/{config,exception,validation}
        │   ├── auth/{controller,service,repository,entity,dto}
        │   ├── salon/{controller,service,repository,entity,dto}
        │   ├── customer/{controller,service,repository,entity,dto}
        │   ├── counseling/{controller,service,repository,entity,dto}
        │   ├── color/{controller,service,repository,entity,dto}
        │   ├── roadmap/{controller,service,repository,entity,dto}
        │   ├── chart/{controller,service,repository,entity,dto}
        │   ├── product/{controller,service,repository,entity,dto}
        │   ├── chat/{controller,service,repository,entity,dto}
        │   ├── reservation/{controller,service,repository,entity,dto}
        │   ├── questionnaire/{controller,service,repository,entity,dto}
        │   ├── notification/{controller,service,repository,entity,dto}
        │   ├── simulation/{controller,service,repository,entity,dto}
        │   └── system
        │       ├── controller/HealthController.java
        │       ├── dto/HealthResponse.java
        │       ├── entity
        │       ├── repository
        │       └── service/HealthService.java
        └── resources
            ├── application.properties
            ├── db/migration
            │   └── V1__create_initial_schema.sql
            └── static
                ├── css/style.css
                ├── images
                ├── js/app.js
                └── index.html
```

初回マイグレーションには、共有された設計に基づく28テーブルと外部キー、
一意制約、入力制約、検索用インデックスを定義しています。削除時に関連する
カルテや履歴が連鎖削除されないよう、外部キーにカスケード削除は設定していません。
この変更はDBスキーマとアプリ実行基盤を用意するもので、現在の画面のサンプルデータを
DBへ読み書きするAPIはまだ実装していません。

## Javaパッケージの方針

機能ごとにパッケージを分け、その機能内に `controller`、`service`、
`repository`、`entity`、`dto` を配置します。たとえば顧客機能は
`jp.hairia.customer.controller`、`jp.hairia.customer.service`、
`jp.hairia.customer.repository`、`jp.hairia.customer.entity`、
`jp.hairia.customer.dto` に分けます。認証、店舗、カウンセリング、
カラー、ロードマップ、カルテ、薬剤、チャット、予約、アンケート、
通知、シミュレーションも同じ構成で追加できます。共通設定・例外・
バリデーションは `jp.hairia.common` にまとめます。

`jp.hairia.system` のヘルスチェックは、この構成の小さな実装例です。
`GET /api/health` は `{"status":"UP"}` を返します。顧客・カルテなどの個人情報を扱うAPIは、
認証・認可を実装してから追加してください。
各機能の空ディレクトリは `.gitkeep` で保持しており、必要な実装を始める際に
置き換えられます。空の層が不要な機能では、無理に全層を使う必要はありません。
