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
画面は `/api` を通してDBへ読み書きします（下記「API」参照）。

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

## デモ起動とアカウント

`demo` プロファイル（既定）では `db/demo/V1000__demo_data.sql` のデモデータが投入されます。
本番では `SPRING_PROFILES_ACTIVE=prod` を指定し、デモデータを入れないでください。

| 種別 | メール | パスワード |
|---|---|---|
| 美容師 | stylist@hairia.test（stylist2 / stylist3 もあり） | password123 |
| お客様 | customer@hairia.test（customer2 / customer3 もあり） | password123 |

お客様は画面から新規登録できます。美容師アカウントは店舗側でDBに発行します。
### QRコード連携
- 美容師: ホームの「QRコード」で、連携用のQRと8文字のコード（10分・1回限り）が表示されます。
- お客様: マイページの「QRコードを読み取る」でカメラ読み取り（または画像から読み取り）、またはコード入力で連携されます。
  スマホのカメラで読むと `/?link=コード` が開き、ログイン（未登録なら新規登録）後に自動で連携されます。
- カメラの利用にはHTTPSが必要です（Codespacesは対応。スマホから開くにはポートを「公開」にしてください）。
- コードはハッシュで保存し、期限切れ・使用済みは無効です。QRに個人情報は含まれません。

## API（要ログイン: `Authorization: Bearer <token>`）

| 機能 | エンドポイント |
|---|---|
| 認証 | `POST /api/auth/signup` `login` `logout` |
| プロフィール | `GET/PUT /api/me` `GET /api/customers` `GET /api/hairdressers` |
| QR連携 | `POST /api/qr`（美容師） `POST /api/qr/redeem`（お客様） |
| カウンセリング・分析 | `POST /api/counselings/analysis`（ロードマップ下書きも作成） |
| ロードマップ | `GET /api/roadmaps` `POST /api/roadmaps/{customerId}/share` |
| カルテ | `GET/POST /api/charts` `PUT /api/charts/{id}` `POST /api/charts/{id}/advance` `/share` |
| チャット | `GET/POST /api/chat/messages` `POST /api/chat/read` |
| 予約 | `GET/POST /api/reservations` `POST /api/reservations/{id}/cancel` |
| アンケート | `POST /api/questionnaires/answers` |
| 操作履歴 | `GET /api/audit`（美容師） |
| 通知 | `GET /api/notifications` `POST /api/notifications/read` |
| パスワード再設定 | `POST /api/auth/password-reset/request` `confirm` |
| 連携解除・退会 | `POST /api/links/{hairdresserId}/end` `POST /api/me/withdraw` |
| ロードマップ修正 | `PUT /api/roadmaps/{customerId}/steps`（修正後は下書きに戻り、再共有が必要） |

お客様には共有済みのカルテ・ロードマップだけが返り、美容師メモは返りません。
AI分析は現在ルールベース（`model_version = rule-v1`）で、`CounselingService` に閉じています。

## 注意事項
- DBマイグレーションは V1（既存）→ V2（拡張）→ V3（パスワード再設定）。デモデータ適用済みのDBに後から V3 を適用できるよう
  `spring.flyway.out-of-order=true` を設定しています。
- パスワード再設定は **メール送信が未実装** です。`demo` プロファイルでは画面にリンクを表示し、それ以外ではサーバーログにトークンを出力します。
  本番では SMTP 等のメール送信を実装してください。
- 画像（アイコン・チャット写真）は `data:image/(jpeg|png|gif|webp);base64,...` のみ受け付けます（サーバー側で検証）。
- サーバーから受け取った文字列は画面側でHTMLとして解釈されないよう無害化しています。

## 未実装・今後
シミュレーション画像生成、似合う髪色診断、メール/プッシュ通知、薬剤の構造化（`product_mixtures`）、
複数店舗をまたぐ予約（現在は連携中の最初の美容師で予約）、QR連携のレート制限、AI分析の実モデル化（現在はルールベース）。
