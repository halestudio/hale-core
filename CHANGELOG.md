## [6.3.2](https://github.com/halestudio/hale-core/compare/v6.3.1...v6.3.2) (2025-11-07)

### Bug Fixes

* fix invalid dependency in BOM ([99d1ca2](https://github.com/halestudio/hale-core/commit/99d1ca20d8cfdbb84918ea24ed5a36a511ee1cdb))

## [6.3.1](https://github.com/halestudio/hale-core/compare/v6.3.0...v6.3.1) (2025-11-07)

### Bug Fixes

* check for InputStream content when reading ShapeInstance ([4783e26](https://github.com/halestudio/hale-core/commit/4783e26127c2ddb3ba342504e036226091840df5)), closes [ING-5018](https://wetransform.atlassian.net/browse/ING-5018)
* **deps:** update dependency com.google.code.gson:gson to v2.13.2 ([aacf633](https://github.com/halestudio/hale-core/commit/aacf633c6c2774141f64e081396562adad69ae08))
* **deps:** update dependency commons-codec:commons-codec to v1.20.0 ([1ff75e1](https://github.com/halestudio/hale-core/commit/1ff75e192bdc5c9fb081761e2af531d730936f88))
* **deps:** update dependency org.hsqldb:hsqldb to v2.7.4 ([160375a](https://github.com/halestudio/hale-core/commit/160375afda2af1794ade53cc23ddb5b52b2062ce))
* **deps:** update dependency org.xerial:sqlite-jdbc to v3.51.0.0 ([30f6868](https://github.com/halestudio/hale-core/commit/30f68683dee0bc8c24e0ea4b4d9d126e2eda54e4))
* **deps:** update transitive dependencies with high vulnerabilities ([bd16512](https://github.com/halestudio/hale-core/commit/bd16512b710974b3f2460cdd7ff6ae6728f4514e))

## [6.3.0](https://github.com/halestudio/hale-core/compare/v6.2.4...v6.3.0) (2025-11-04)

### Features

* add regex Matcher to Groovy restriction whitelist ([99ebc4a](https://github.com/halestudio/hale-core/commit/99ebc4a9c214fb1f992522a617c830808cdcb09c)), closes [ING-4874](https://wetransform.atlassian.net/browse/ING-4874)

### Bug Fixes

* **deps:** update deegree to v3.6.2 ([c258ebb](https://github.com/halestudio/hale-core/commit/c258ebb54cd486e4b90c3fe7e12a74834dfcbb9d))
* **deps:** update dependency com.google.guava:guava to v33.5.0-jre ([2e3be82](https://github.com/halestudio/hale-core/commit/2e3be82007967d7461dc93408a50cdfeb6fb528e))
* **deps:** update dependency com.typesafe:config to v1.4.5 ([0a04eff](https://github.com/halestudio/hale-core/commit/0a04eff856bc53be589672ac36d6cfe4d802eda5))
* **deps:** update dependency jakarta.xml.bind:jakarta.xml.bind-api to v4.0.4 ([7fac496](https://github.com/halestudio/hale-core/commit/7fac4962c5b8dc6c97699e0af419586b0021fd96))
* **deps:** update dependency org.apache.logging.log4j:log4j-to-slf4j to v2.25.2 ([4a1282d](https://github.com/halestudio/hale-core/commit/4a1282d463e3f8d8d09118920ebedd83d8fee2e4))
* **deps:** update dependency org.glassfish.jaxb:jaxb-runtime to v4.0.6 ([018f676](https://github.com/halestudio/hale-core/commit/018f676718db9cac4cc9c5d89fc9957c75b8258a))
* **deps:** update dependency org.postgresql:postgresql to v42.7.8 ([bb45ddc](https://github.com/halestudio/hale-core/commit/bb45ddc494da469876c4151802ddab9e49f247c9))
* **deps:** update dependency to.wetransform.offline-resources:eu.esdihumboldt.util.resource.schemas.opengis.net to v2025 ([9742d0f](https://github.com/halestudio/hale-core/commit/9742d0f76da3259beeee78403e3cdf14eb6b504a))
* **deps:** update logback dependencies to v1.5.19 ([74f59c9](https://github.com/halestudio/hale-core/commit/74f59c9a470b2bde182c1e806c6c91074c293e97))
* **deps:** update logback dependencies to v1.5.20 ([b462540](https://github.com/halestudio/hale-core/commit/b4625400f2c915849271ef5c53fea197deae0393))
* **deps:** update protobuf monorepo to v4.32.1 ([cf627e7](https://github.com/halestudio/hale-core/commit/cf627e7f65e39127026351c11370a83878fdfa83))
* **deps:** update protobuf monorepo to v4.33.0 ([a0f4197](https://github.com/halestudio/hale-core/commit/a0f4197705cdfe0cb4b36c8dc88e4f6c791fb9e3))
* **deps:** update schemacrawler to v16.28.1 ([6b57c0f](https://github.com/halestudio/hale-core/commit/6b57c0f2a228372f43a696ee735ec4d46152799a))
* **deps:** update schemacrawler to v16.28.2 ([272be8f](https://github.com/halestudio/hale-core/commit/272be8fa8999c2d2d7a10d9d43b46c719cb555ae))
* **deps:** update schemacrawler to v16.28.3 ([82957b7](https://github.com/halestudio/hale-core/commit/82957b7c66f7e264d1488bde101a43adb06e1af5))
* **deps:** update schemacrawler to v16.29.1 ([815f41d](https://github.com/halestudio/hale-core/commit/815f41db675c106795a4fa2b6141f612262412a6))
* **deps:** update spring core to v6.2.11 ([bd1822c](https://github.com/halestudio/hale-core/commit/bd1822c0436573eba2191a8d34834722577b63d7))
* **deps:** update spring core to v6.2.12 ([70909eb](https://github.com/halestudio/hale-core/commit/70909eb415f9f878289264171785ad26b3876731))

## [6.2.4](https://github.com/halestudio/hale-core/compare/v6.2.3...v6.2.4) (2025-09-09)

### Bug Fixes

* **deps:** expose groovy-core as API dependency ([d216f89](https://github.com/halestudio/hale-core/commit/d216f89fae57084097be00fe44282f60f44319d9))
* **deps:** update deegree to v3.6.0 ([407ca8f](https://github.com/halestudio/hale-core/commit/407ca8f71a2aace14da80d586ce50ef1579bf3f9))
* **deps:** update deegree to v3.6.1 ([966f507](https://github.com/halestudio/hale-core/commit/966f5076478f78708bb70c9d4f1488f5e99a0718))
* **deps:** update dependency org.yaml:snakeyaml to v2.5 ([69a2b14](https://github.com/halestudio/hale-core/commit/69a2b1441cac4728c7ea8feebe4f3a87c722c188))
* **deps:** update geotools to v32.4 ([7feef8a](https://github.com/halestudio/hale-core/commit/7feef8a80067fa3a85e0321661a193e92ce48cad))
* **deps:** update protobuf monorepo to v4.32.0 ([579e5b4](https://github.com/halestudio/hale-core/commit/579e5b46d2ef314a51119a3707d48d7d78248960))
* **deps:** update schemacrawler to v16.27.1 ([4102a6c](https://github.com/halestudio/hale-core/commit/4102a6c2faa40b56df6a047d43ea650abafdeb68))
* **deps:** update schemacrawler to v16.27.2 ([cb31e19](https://github.com/halestudio/hale-core/commit/cb31e196189672d680099151e12e6d01ff4b1b5b))
* **deps:** update spring core to v6.2.10 ([b64d4d5](https://github.com/halestudio/hale-core/commit/b64d4d514c2dfb5f22dbad186d004e8ead82108c))
* **ShapefileReader:** check file existence in case of custom urls ([ea3abff](https://github.com/halestudio/hale-core/commit/ea3abff1d6a5c96edd1074cfe3627ea952dcfb66)), closes [ING-4811](https://wetransform.atlassian.net/browse/ING-4811)

## [6.2.3](https://github.com/halestudio/hale-core/compare/v6.2.2...v6.2.3) (2025-08-05)

### Bug Fixes

* **deps:** update apache-poi monorepo to v5.4.1 ([432c852](https://github.com/halestudio/hale-core/commit/432c852f905dbed90f8d4343cb991a572defa967))
* **deps:** update batik to v1.19 ([616beb5](https://github.com/halestudio/hale-core/commit/616beb553cff2532c290d0e335dfb1b1bb8114aa))
* **deps:** update dependency com.google.guava:guava to v33.4.6-jre ([b5399f0](https://github.com/halestudio/hale-core/commit/b5399f0b321c41da1da42a307ba0d9d9cd950d2c))
* **deps:** update dependency com.google.guava:guava to v33.4.8-jre ([7baad53](https://github.com/halestudio/hale-core/commit/7baad53de18034e6c38f8e069ace70b9001ce2bd))
* **deps:** update dependency com.typesafe:config to v1.4.4 ([abe647d](https://github.com/halestudio/hale-core/commit/abe647db3823b05924d6be37fba9b3af4e84b105))
* **deps:** update dependency commons-codec:commons-codec to v1.19.0 ([ad42eff](https://github.com/halestudio/hale-core/commit/ad42efff1c9a3b6d2138a5532300fb861a551d95))
* **deps:** update dependency commons-io:commons-io to v2.19.0 ([7843134](https://github.com/halestudio/hale-core/commit/784313451a0a53d9a56b150029d6ff509abbf6b5))
* **deps:** update dependency commons-io:commons-io to v2.20.0 ([ff578e8](https://github.com/halestudio/hale-core/commit/ff578e838b3414e26a0bde5fa4817b5a20333dfa))
* **deps:** update dependency info.picocli:picocli to v4.7.7 ([5a04a4d](https://github.com/halestudio/hale-core/commit/5a04a4dd2d820ba2dec21ca942fe66486c410e9b))
* **deps:** update dependency joda-time:joda-time to v2.14.0 ([3956cd5](https://github.com/halestudio/hale-core/commit/3956cd56b8c5d5a7521db9cbc6e3d7c0599f6cb5))
* **deps:** update dependency net.postgis:postgis-jdbc to v2025 ([2047720](https://github.com/halestudio/hale-core/commit/2047720ccc8306aa50c90ca50ade9c2aa2aae643))
* **deps:** update dependency net.postgis:postgis-jdbc to v2025.1.1 ([441c12a](https://github.com/halestudio/hale-core/commit/441c12a3411ba02ae4e61c1917811b5128383ecd))
* **deps:** update dependency org.apache.logging.log4j:log4j-to-slf4j to v2.25.0 ([69a3cd5](https://github.com/halestudio/hale-core/commit/69a3cd5dfbc7ba5191d6ea1233d67fdeaef7748b))
* **deps:** update dependency org.apache.logging.log4j:log4j-to-slf4j to v2.25.1 ([70481da](https://github.com/halestudio/hale-core/commit/70481daa959564232c8b0ff369f0e76effec5ec9))
* **deps:** update dependency org.postgresql:postgresql to v42.7.6 ([b2f5e96](https://github.com/halestudio/hale-core/commit/b2f5e9607d990495c183504cc8b46a616d9c011a))
* **deps:** update dependency org.postgresql:postgresql to v42.7.7 ([9655b00](https://github.com/halestudio/hale-core/commit/9655b00c0d33150fc8d19a61bd83fa8a44ea4eaa))
* **deps:** update dependency org.testcontainers:testcontainers to v1.20.5 ([3ed48c9](https://github.com/halestudio/hale-core/commit/3ed48c96ff0c614623e344d6ba056ea9e869b554))
* **deps:** update dependency org.xerial:sqlite-jdbc to v3.49.1.0 ([4c40573](https://github.com/halestudio/hale-core/commit/4c40573756798f41696633a4eca41598ec60a203))
* **deps:** update dependency org.xerial:sqlite-jdbc to v3.50.1.0 ([18ac82b](https://github.com/halestudio/hale-core/commit/18ac82b19781ecac63124892e3594c23f39b6531))
* **deps:** update dependency org.xerial:sqlite-jdbc to v3.50.2.0 ([039fef2](https://github.com/halestudio/hale-core/commit/039fef2a3e7676f4ffa747c96ff7d59978f89ecb))
* **deps:** update dependency org.xerial:sqlite-jdbc to v3.50.3.0 ([b4d84cc](https://github.com/halestudio/hale-core/commit/b4d84cc5880bac6abe4d9e53b5bb1201257bd51e))
* **deps:** update dependency org.yaml:snakeyaml to v2.4 ([abd2895](https://github.com/halestudio/hale-core/commit/abd2895162b156e2864d39f7618c9fb18d1d6be0))
* **deps:** update eclipse.collections to v13 ([b8fc0a6](https://github.com/halestudio/hale-core/commit/b8fc0a61f010ba7f78d37c47836bff233e95ad93))
* **deps:** update geotools to v32.3 ([e8e2ff2](https://github.com/halestudio/hale-core/commit/e8e2ff2c6269c517855dd9b97c0f3b904d5f2df2))
* **deps:** update jackson monorepo to v2.18.3 ([5cafc9a](https://github.com/halestudio/hale-core/commit/5cafc9ae1b1da4d0855f28132d8c4b88eabd7a47))
* **deps:** update logback dependencies to v1.5.17 ([30386c2](https://github.com/halestudio/hale-core/commit/30386c28dda652c36410d36c3319c5c3122aceb7))
* **deps:** update logback dependencies to v1.5.18 ([63fce80](https://github.com/halestudio/hale-core/commit/63fce806d10323c64437bfcafdbabacb237126e9))
* **deps:** update protobuf monorepo to v4.30.0 ([079342c](https://github.com/halestudio/hale-core/commit/079342cb113b0bf9de4c6a111a26112d38e291a8))
* **deps:** update protobuf monorepo to v4.30.1 ([a118569](https://github.com/halestudio/hale-core/commit/a1185694d4f463f7c4efcfe0c52118d9383417e8))
* **deps:** update protobuf monorepo to v4.30.2 ([0188fe0](https://github.com/halestudio/hale-core/commit/0188fe0184f15c6b84661794400f152f70ad775e))
* **deps:** update protobuf monorepo to v4.31.0 ([1163698](https://github.com/halestudio/hale-core/commit/116369874de14c0ca29f5e00fad49ccadad842bc))
* **deps:** update protobuf monorepo to v4.31.1 ([cb54035](https://github.com/halestudio/hale-core/commit/cb54035c7776e693b0c58a995cd3d7dcf0c5918b))
* **deps:** update schemacrawler to v16.25.3 ([b7ca350](https://github.com/halestudio/hale-core/commit/b7ca350a8ecaa5f0a381a619f96f067d24b1e78d))
* **deps:** update schemacrawler to v16.25.4 ([9f9ada6](https://github.com/halestudio/hale-core/commit/9f9ada6baab197494beb15fc939332b74c2fc240))
* **deps:** update schemacrawler to v16.25.5 ([c545671](https://github.com/halestudio/hale-core/commit/c5456712c5951c202eaed0b9cec4878e90ce0650))
* **deps:** update schemacrawler to v16.26.1 ([b2079ec](https://github.com/halestudio/hale-core/commit/b2079ec10831812f954d5376096ca38cc660d0b7))
* **deps:** update schemacrawler to v16.26.2 ([6dd20d9](https://github.com/halestudio/hale-core/commit/6dd20d9e8d8dc4f7bf5bdc21044ac00bb7f4e97f))
* **deps:** update schemacrawler to v16.26.3 ([4f944b3](https://github.com/halestudio/hale-core/commit/4f944b38a45693b34e98cbea570028fd389361e4))
* **deps:** update slf4j monorepo to v2.0.17 ([9dda7e4](https://github.com/halestudio/hale-core/commit/9dda7e44776ddca754c09f375e97737b087a83c4))
* **deps:** update spring core to v6.2.3 ([861801f](https://github.com/halestudio/hale-core/commit/861801f65c7d442b5771bc1a44e04f40f35d0e62))
* **deps:** update spring core to v6.2.4 ([0ca1124](https://github.com/halestudio/hale-core/commit/0ca112496b4a13de1087522da318e1fc728cda32))
* **deps:** update spring core to v6.2.5 ([04c3891](https://github.com/halestudio/hale-core/commit/04c3891fc7be29c7745dde8714da254edc68a8c7))
* **deps:** update spring core to v6.2.6 ([452c7a3](https://github.com/halestudio/hale-core/commit/452c7a38b929de710eee5c66aeaf492303e706a0))
* **deps:** update spring core to v6.2.7 ([eb3dcee](https://github.com/halestudio/hale-core/commit/eb3dcee2ee53e054d2e9563ea39e90e8998c48b2))
* **deps:** update spring core to v6.2.8 ([1fc95e5](https://github.com/halestudio/hale-core/commit/1fc95e5e16aea17bb03c540f6b198460fb0e7733))
* **deps:** update spring core to v6.2.9 ([6d6296d](https://github.com/halestudio/hale-core/commit/6d6296d585017a7d0ed7ea20439d2d066dd6254a))
* download all Shapefiles when given URL contains FIX file ([3b2268b](https://github.com/halestudio/hale-core/commit/3b2268bb98489004632488d53ba01ea85f062704)), closes [ING-4811](https://wetransform.atlassian.net/browse/ING-4811)

## [6.2.2](https://github.com/halestudio/hale-core/compare/v6.2.1...v6.2.2) (2025-02-11)

### Bug Fixes

* **deps:** remove not needed dependency from Excel format support ([fb52fa5](https://github.com/halestudio/hale-core/commit/fb52fa55ed962c305b4bc97d9209fdb8bb7e966b))

## [6.2.1](https://github.com/halestudio/hale-core/compare/v6.2.0...v6.2.1) (2025-02-10)

### Bug Fixes

* **deps:** remove explicit dependency to mil.nga.geopackage:geopackage-core ([dc94cad](https://github.com/halestudio/hale-core/commit/dc94cad6bf6dae71bc9d8734afdc548ddb23dc8d))
* **deps:** update allure to v2.29.1 ([4fa2ffe](https://github.com/halestudio/hale-core/commit/4fa2ffedae4120e9cd7720f74198c536a14468a4))
* **deps:** update apache-poi monorepo to v5.4.0 ([c28ed1c](https://github.com/halestudio/hale-core/commit/c28ed1c4a4d1438de74ea5061d3bad8fbca78b87))
* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.18.2 ([d02a6bd](https://github.com/halestudio/hale-core/commit/d02a6bd1cc0d7399e2edbef3c116eabcef2e85e6))
* **deps:** update dependency com.google.guava:guava to v33.4.0-jre ([9efdcd3](https://github.com/halestudio/hale-core/commit/9efdcd3b99843c4ff0b7f89fd35ed85da7a019dc))
* **deps:** update dependency commons-codec:commons-codec to v1.17.2 ([fc1faa5](https://github.com/halestudio/hale-core/commit/fc1faa5753a7ea236ca743d3e0db0f242cbf2a6f))
* **deps:** update dependency commons-codec:commons-codec to v1.18.0 ([b0a56fe](https://github.com/halestudio/hale-core/commit/b0a56fe87e083dd557e07bd2922d06baa7323f31))
* **deps:** update dependency commons-io:commons-io to v2.18.0 ([6597bb2](https://github.com/halestudio/hale-core/commit/6597bb25f38f97fe680e593d57edb974384fc32b))
* **deps:** update dependency info.picocli:picocli to v4.7.6 ([467b035](https://github.com/halestudio/hale-core/commit/467b035568ab7ab63fad05b6b7c6f8077a8e6605))
* **deps:** update dependency joda-time:joda-time to v2.13.1 ([dbb3f53](https://github.com/halestudio/hale-core/commit/dbb3f53cb32437036ee9909b44309f116e73a5fd))
* **deps:** update dependency net.postgis:postgis-jdbc to v2024 ([5e777ae](https://github.com/halestudio/hale-core/commit/5e777ae845da81cd142a48b4a452e0157113042a))
* **deps:** update dependency org.apache.logging.log4j:log4j-to-slf4j to v2.24.3 ([b5c299f](https://github.com/halestudio/hale-core/commit/b5c299ff3f3a4db5f2effbc1695cdfb9140e0845))
* **deps:** update dependency org.apache.xmlbeans:xmlbeans to v5.2.2 ([97f7ac6](https://github.com/halestudio/hale-core/commit/97f7ac61f0529c2260987d4e20208521ba0d034d))
* **deps:** update dependency org.apache.xmlbeans:xmlbeans to v5.3.0 ([a6afdf9](https://github.com/halestudio/hale-core/commit/a6afdf9d61f1446d4bfe271ac551805f68f57ef6))
* **deps:** update dependency org.assertj:assertj-core to v3.27.2 ([2b19c63](https://github.com/halestudio/hale-core/commit/2b19c63e77dd0bcdf005ae983b0bd8790095a731))
* **deps:** update dependency org.assertj:assertj-core to v3.27.3 ([01823bd](https://github.com/halestudio/hale-core/commit/01823bd245b299a0fb2404b851e9f343da92f9a4))
* **deps:** update dependency org.postgresql:postgresql to v42.7.5 ([26be8e8](https://github.com/halestudio/hale-core/commit/26be8e80dc12f99014f01ea5340edf761846bce5))
* **deps:** update dependency org.testcontainers:testcontainers to v1.20.4 ([0b4f474](https://github.com/halestudio/hale-core/commit/0b4f47417f394fbba5427cbee2f097d08f5f93ca))
* **deps:** update dependency org.xerial:sqlite-jdbc to v3.47.1.0 ([2da11a4](https://github.com/halestudio/hale-core/commit/2da11a47e4e6e1dbec81860d3e50f9af8eb73414))
* **deps:** update dependency org.xerial:sqlite-jdbc to v3.47.2.0 ([196d20a](https://github.com/halestudio/hale-core/commit/196d20a21928035c544eba95100f25626809bdae))
* **deps:** update dependency org.xerial:sqlite-jdbc to v3.48.0.0 ([c2a6514](https://github.com/halestudio/hale-core/commit/c2a65142e562ac2c6e2cb6e1babe92ff4a3d23b5))
* **deps:** update dependency org.xerial:sqlite-jdbc to v3.49.0.0 ([7c9c8a0](https://github.com/halestudio/hale-core/commit/7c9c8a05f20c14b26a2e57396dfa08b110db9fdf))
* **deps:** update dependency to.wetransform.offline-resources:eu.esdihumboldt.util.resource.schemas.inspire to v2025 ([cbb78a7](https://github.com/halestudio/hale-core/commit/cbb78a7e662380b33992a4f653e773aca2e179f7))
* **deps:** update dependency to.wetransform.offline-resources:eu.esdihumboldt.util.resource.schemas.inspire to v2025.2.4 ([378dadd](https://github.com/halestudio/hale-core/commit/378daddc7bbee9c17db5736c3293b48fa423168e))
* **deps:** update dependency us.fatehi:schemacrawler to v16 ([1efaa2e](https://github.com/halestudio/hale-core/commit/1efaa2e4cfadbb7125c04bee7a9457ed6ececc94))
* **deps:** update geopackage to v6 ([f0d3823](https://github.com/halestudio/hale-core/commit/f0d382388f2409878e76e7a486057c965b515379))
* **deps:** update geotools to v32 ([f474ca1](https://github.com/halestudio/hale-core/commit/f474ca172e6bce852e44f2687449efb4ba8cfa9f))
* **deps:** update geotools to v32.2 ([5cc6264](https://github.com/halestudio/hale-core/commit/5cc6264c8121832d2518003e026158d8feb3d748))
* **deps:** update logback dependencies to v1.5.12 ([d3a1bcf](https://github.com/halestudio/hale-core/commit/d3a1bcff9226abe139f67107a6d0a2e01b9796f6))
* **deps:** update logback dependencies to v1.5.16 ([a26bfdd](https://github.com/halestudio/hale-core/commit/a26bfdd2d489c6e929fee49c3ba6574a0c24cf9d))
* **deps:** update offline resources to v2024.11.27 ([26af8c3](https://github.com/halestudio/hale-core/commit/26af8c336252f277c39f70d07fe75caff929a716))
* **deps:** update offline resources to v2025 ([8f2f950](https://github.com/halestudio/hale-core/commit/8f2f95067855c94a137ea7d8d9d1d8a716dc2c3b))
* **deps:** update protobuf to v4.29.1 ([6dce729](https://github.com/halestudio/hale-core/commit/6dce729f9e980fe667f8839fb1a3d3177ea89ed0))
* **deps:** update protobuf to v4.29.3 ([fee6d56](https://github.com/halestudio/hale-core/commit/fee6d56c605e19e3344d471f8573857c73542786))
* **deps:** update schemacrawler to v16.24.1 ([10e36c0](https://github.com/halestudio/hale-core/commit/10e36c071422fb47ed2706d8a982c39fc0e294d9))
* **deps:** update schemacrawler to v16.25.1 ([6a8095b](https://github.com/halestudio/hale-core/commit/6a8095bf78dded749c9b9cf9364236ab38294824))
* **deps:** update schemacrawler to v16.25.2 ([560b5b2](https://github.com/halestudio/hale-core/commit/560b5b2df082357bd8d35edb1cb6d1bedbbf4f93))
* **deps:** update spring core to v6.2.0 ([fa613e7](https://github.com/halestudio/hale-core/commit/fa613e71b24670b7b190b61a98561251e6480dbe))
* **deps:** update spring core to v6.2.1 ([90e6216](https://github.com/halestudio/hale-core/commit/90e62161248070ba852abfeaf97bc1bac37cdad5))
* **deps:** update spring core to v6.2.2 ([6555c60](https://github.com/halestudio/hale-core/commit/6555c60478c4703380c1335d40f2fb7f951b69f3))
* log log4j2 via slf4j ([e26e13b](https://github.com/halestudio/hale-core/commit/e26e13bdd8d3026d8683de13b768e3cf2d456ac1))

## [6.2.0](https://github.com/halestudio/hale-core/compare/v6.1.0...v6.2.0) (2024-12-03)

### Features

* **geopackage:** prefer non-GML namespace on multiple candidates for property ([9c4ecbe](https://github.com/halestudio/hale-core/commit/9c4ecbe1c568c60ea19a20024b01f172b0f67ef6)), closes [ING-4543](https://wetransform.atlassian.net/browse/ING-4543)
* **shp:** prefer non-GML namespace on multiple candidates for property ([0089368](https://github.com/halestudio/hale-core/commit/00893689ab79003cf77a6c8b1242bb8367341945)), closes [ING-4543](https://wetransform.atlassian.net/browse/ING-4543)

### Bug Fixes

* adapt name of system property for join handler ([1b47ecf](https://github.com/halestudio/hale-core/commit/1b47ecfabb7a6de37ff3ec150ec18857fa72d630))
* fix resolving http/https URIs with jar base URIs ([256a93c](https://github.com/halestudio/hale-core/commit/256a93ca0afea19fadb4201fc057db7ca00a6378))

## [6.1.0](https://github.com/halestudio/hale-core/compare/v6.0.0...v6.1.0) (2024-11-14)

### Features

* add IntRange to Groovy restriction whitelist ([0a78400](https://github.com/halestudio/hale-core/commit/0a7840005b3fffc9d85bd67b2a3ccad613618373))
* extend Shapefile writer with log messages on written files ([9b06d1e](https://github.com/halestudio/hale-core/commit/9b06d1eb40ac79c7c2bfadbaf19d92c9bc64916d)), closes [ING-4479](https://wetransform.atlassian.net/browse/ING-4479)

### Bug Fixes

* **deps:** update batik to v1.18 ([cdfd68c](https://github.com/halestudio/hale-core/commit/cdfd68c4bbe0ab5d83ac008acec72559311a69c4))
* **deps:** update dependency ch.qos.logback:logback-core to v1.5.8 ([7971b98](https://github.com/halestudio/hale-core/commit/7971b981b499a0b1357867e1418d02e7757406e3))
* **deps:** update dependency com.fasterxml.jackson.core:jackson-annotations to v2.18.0 ([b5362e5](https://github.com/halestudio/hale-core/commit/b5362e5ad5091b0216cfb055c3cf065a53efa219))
* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.17.2 ([c2a84ea](https://github.com/halestudio/hale-core/commit/c2a84ea2ac1712d85db35459fb78f91162be89db))
* **deps:** update dependency com.google.code.findbugs:annotations to v3 ([8d1e158](https://github.com/halestudio/hale-core/commit/8d1e1583dd75565c572ada1a5206839379e1488c))
* **deps:** update dependency com.google.guava:guava to v33 ([90815eb](https://github.com/halestudio/hale-core/commit/90815eb71cfdb12e8714181b1fc6bef42ca332b2))
* **deps:** update dependency com.google.guava:guava to v33.3.1-jre ([e971a15](https://github.com/halestudio/hale-core/commit/e971a15b1efca155b2f02537fc3252a4695a8a2a))
* **deps:** update dependency commons-io:commons-io to v2.17.0 ([64da035](https://github.com/halestudio/hale-core/commit/64da03516faadb4733dadd9fdf217d97c16baf68))
* **deps:** update dependency joda-time:joda-time to v2.13.0 ([8d9bebe](https://github.com/halestudio/hale-core/commit/8d9bebedd6d44ebc4b5aa303cd4a62e5a667d829))
* **deps:** update dependency net.sf.saxon:saxon-he to v9.9.1-8 ([136ebf4](https://github.com/halestudio/hale-core/commit/136ebf4f2f66a5b314d6fa212ecda96bc0950a0b))
* **deps:** update dependency net.sf.ucanaccess:ucanaccess to v5 ([ddb987d](https://github.com/halestudio/hale-core/commit/ddb987deba13c259823c8b2db97d9917fff8a3d0))
* **deps:** update dependency org.testcontainers:testcontainers to v1.20.2 ([9579e8f](https://github.com/halestudio/hale-core/commit/9579e8fdf7e42277f65096f71541825efee5e045))
* **deps:** update dependency org.xerial:sqlite-jdbc to v3.46.1.3 ([0bd344f](https://github.com/halestudio/hale-core/commit/0bd344fe0b36bc24cc0dddb464b3252124c6ac04))
* **deps:** update geotools to v29.6 ([2f6b791](https://github.com/halestudio/hale-core/commit/2f6b791c54946c0bd87a94c1f327a75f541e613a))
* **deps:** update logback dependencies to v1.5.11 ([a70f5c2](https://github.com/halestudio/hale-core/commit/a70f5c21d454ff73133a142bcafb1cdf45d5c38e))
* **deps:** update poi to v5.3.0 ([1f07614](https://github.com/halestudio/hale-core/commit/1f0761472c800054a6860493b58974df608adac5))
* **deps:** update protobuf to v3.25.4 ([b2d02aa](https://github.com/halestudio/hale-core/commit/b2d02aa93fd7322ebd9e550d438987f0f613806b))
* **deps:** update protobuf to v4 ([b0ac427](https://github.com/halestudio/hale-core/commit/b0ac4277433364f5294afbe127a41069b2e17ecd))
* **deps:** update protobuf to v4.28.2 ([a01dcc6](https://github.com/halestudio/hale-core/commit/a01dcc67fda9a74107cf310b81914a455256132a))
* **deps:** update spring core to v6 ([7f136c8](https://github.com/halestudio/hale-core/commit/7f136c8c4fa3243bb159822dcc5d532e31cb192d))
* **deps:** update spring core to v6.1.13 ([179f4ac](https://github.com/halestudio/hale-core/commit/179f4ace9110c56b6ceeacb2343ed4bdca7cbffd))
* **deps:** update spring core to v6.1.14 ([1cf95e9](https://github.com/halestudio/hale-core/commit/1cf95e93e0bccb0e6376bbdf277c453a682c55d5))
* wait until adding instance to sink is complete ([eae6e82](https://github.com/halestudio/hale-core/commit/eae6e827c7f48fee7bc812a21f1cb10e5b74bf8b)), closes [ING-4479](https://wetransform.atlassian.net/browse/ING-4479)

### Performance Improvements

* make index join/merge handlers optional ([181304f](https://github.com/halestudio/hale-core/commit/181304f4f221bb0123572c37a3cd789beb513f36)), closes [ING-4464](https://wetransform.atlassian.net/browse/ING-4464)

## [6.0.0](https://github.com/halestudio/hale-core/compare/v5.3.0...v6.0.0) (2024-09-05)


### ⚠ BREAKING CHANGES

* **deps:** Downstream dependencies need to use newer slf4j backend
implementation.
* Core libraries are now no longer part of the hale
studio repository but were moved to this repository (hale-core).

### Features

* add feature project for CLI dependencies ([62fe49d](https://github.com/halestudio/hale-core/commit/62fe49dabbe2dd99556969e2deba4cd62506796e))
* **shp:** support detecting type based on file name with result_ prefix ([299de8c](https://github.com/halestudio/hale-core/commit/299de8cde2a93fc546060c605833fd354ce0699b)), closes [ING-4416](https://wetransform.atlassian.net/browse/ING-4416)
* **shp:** support writing date fields with other bindings than Date ([cf410c0](https://github.com/halestudio/hale-core/commit/cf410c05441cdf97f79dd9ada97732527566fb54))


### Bug Fixes

* **deps:** update allure to v2.29.0 ([3dd9b7f](https://github.com/halestudio/hale-core/commit/3dd9b7ff92dbc452d3b6ef9737ba0dd0df871519))
* **deps:** update apache.http.client to v4.5.14 ([d44cc4d](https://github.com/halestudio/hale-core/commit/d44cc4dd603aa882c6da42402734c8adf85c62b9))
* **deps:** update batik to v1.17 ([12939e5](https://github.com/halestudio/hale-core/commit/12939e5efb33b24934957e3d80ec23cca7e3da42))
* **deps:** update dependency com.google.guava:guava to v27.1-jre ([d19e53c](https://github.com/halestudio/hale-core/commit/d19e53c0a6489e0a713cac52dcb734907748d08a))
* **deps:** update dependency com.typesafe:config to v1.4.3 ([ce6c6de](https://github.com/halestudio/hale-core/commit/ce6c6de585a580311a74eb87d410c1b71e61214a))
* **deps:** update dependency commons-codec:commons-codec to v1.17.1 ([a201a8a](https://github.com/halestudio/hale-core/commit/a201a8a1685d8713d8903a9215e78204e34e24a9))
* **deps:** update dependency commons-io:commons-io to v2.16.1 ([ce9d77d](https://github.com/halestudio/hale-core/commit/ce9d77d77b800bec01a32dd06ec33545907c1329))
* **deps:** update dependency jakarta.xml.bind:jakarta.xml.bind-api to v4.0.2 ([d0111ac](https://github.com/halestudio/hale-core/commit/d0111ac1f98f6b8a7c951c3d936833fc7d0665a2))
* **deps:** update dependency joda-time:joda-time to v2.12.7 ([430ca72](https://github.com/halestudio/hale-core/commit/430ca723ebab8ac259521d9c052bf42e9a8bb01e))
* **deps:** update dependency net.sf.trove4j:trove4j to v2.1.0 ([0a5a258](https://github.com/halestudio/hale-core/commit/0a5a258edde087b21d3a1817b735b72d3f4e1605))
* **deps:** update dependency net.sf.ucanaccess:ucanaccess to v3.0.7 ([80de49c](https://github.com/halestudio/hale-core/commit/80de49c0ebf4385e7b0307870eb63a20d0bac5d6))
* **deps:** update dependency org.apache.httpcomponents:httpcore to v4.4.16 ([43b2a7f](https://github.com/halestudio/hale-core/commit/43b2a7f46854cab88f60fd26a7f5b67d82fa3ead))
* **deps:** update dependency org.apache.xmlbeans:xmlbeans to v5.2.1 ([b871341](https://github.com/halestudio/hale-core/commit/b8713413f25508e2ddb9ec3f33beed9436052882))
* **deps:** update dependency org.aspectj:aspectjweaver to v1.9.22.1 ([b61eaf2](https://github.com/halestudio/hale-core/commit/b61eaf209254be9531bfa84f98ca6482c59e29b4))
* **deps:** update dependency org.assertj:assertj-core to v3.26.3 ([aae94ba](https://github.com/halestudio/hale-core/commit/aae94ba5ed31ff0c733d0d60f9aadd7f4803117f))
* **deps:** update dependency org.glassfish.jaxb:jaxb-runtime to v4.0.5 ([66ae7e8](https://github.com/halestudio/hale-core/commit/66ae7e8e4ccb406b6a0c3d1bb6b372d3b932d39b))
* **deps:** update dependency org.locationtech.jts:jts-core to v1.20.0 ([244c1b6](https://github.com/halestudio/hale-core/commit/244c1b60f50ca679627decb01047c29a1463b27e))
* **deps:** update dependency org.postgresql:postgresql to v42.7.3 ([2bde5b1](https://github.com/halestudio/hale-core/commit/2bde5b1cf98225d8a091e5f24254289ffd9ccb35))
* **deps:** update dependency org.postgresql:postgresql to v42.7.4 ([a6d4ce1](https://github.com/halestudio/hale-core/commit/a6d4ce175dfab0889f268667657e2cc88a331903))
* **deps:** update dependency org.xerial:sqlite-jdbc to v3.46.1.0 ([682b7e9](https://github.com/halestudio/hale-core/commit/682b7e99be54eb4a406d115f1e8151fc54307fba))
* **deps:** update dependency org.yaml:snakeyaml to v2.3 ([576230d](https://github.com/halestudio/hale-core/commit/576230d1479e98781ea6a81baaf11b85c4d80b3f))
* **deps:** update dependency us.fatehi:schemacrawler to v12.06.03 ([4b1afaa](https://github.com/halestudio/hale-core/commit/4b1afaab68d97ac62a4211c4550dffbe857af282))
* **deps:** update flexmark to v0.64.8 ([e6c7eaa](https://github.com/halestudio/hale-core/commit/e6c7eaafcd01763013b0566e93f72b9e449b3eb8))
* **deps:** update geopackage to v3.5.0 ([eea30f3](https://github.com/halestudio/hale-core/commit/eea30f3ef82772309f80d58faff5d1942dc2e89d))
* **deps:** update logback dependencies to v1.5.7 ([11a25e6](https://github.com/halestudio/hale-core/commit/11a25e6899c399b3473cad2b3a079338ce286449))
* **deps:** update slf4j monorepo to v2 ([97ceea8](https://github.com/halestudio/hale-core/commit/97ceea84d386a4f8e95cf16c8059012acd6eaf8a))
* **deps:** update spring core to v5.3.39 ([528f552](https://github.com/halestudio/hale-core/commit/528f55224a772391f9c12edfae52e890078f49aa))
* download linked objects in a WFS using "resolvedepth" ([0243b38](https://github.com/halestudio/hale-core/commit/0243b38289b5c5c1e5bd4b82f7ebdc5f44ca22fd)), closes [ING-4128](https://wetransform.atlassian.net/browse/ING-4128)
* make eclipse utilities API dependency ([e7ed836](https://github.com/halestudio/hale-core/commit/e7ed83622734e96a71eb2e201d36ca9f64f77920))
* make some dependencies API dependencies ([a54a3b0](https://github.com/halestudio/hale-core/commit/a54a3b0bb3c570a8a497c5c81a97e9ce5322adb8))
* resolve extension contributions from local projects w/o manifest ([4760a37](https://github.com/halestudio/hale-core/commit/4760a3742424b2c1d9c2e06a5022e6f0051f8e27))


### Build System

* migrate bundles that are not only relevant for UI to Gradle ([444098e](https://github.com/halestudio/hale-core/commit/444098edea8077b0e11324d6947993f57a9a900b)), closes [ING-4375](https://wetransform.atlassian.net/browse/ING-4375)

## [5.3.0](https://github.com/halestudio/hale/compare/v5.2.1...v5.3.0) (2024-08-07)


### Features

* support loading Shapefiles for XML schemas ([0f64ad3](https://github.com/halestudio/hale/commit/0f64ad32cd12bf375410a52a9b6672e254787175))
* support loading single Shapefile from folder ([eb83a2f](https://github.com/halestudio/hale/commit/eb83a2f6f8b8351e8d8908e7e45e71b2694b98ba))


### Bug Fixes

* avoid arbitrary file access during archive extraction ("Zip Slip") ([5c82a4b](https://github.com/halestudio/hale/commit/5c82a4baa05f583326b60a0c5c6f4a72c8269717))
* **deps:** update dependency to.wetransform.offlineresources.feature to v2024.7.31.bnd-fohraq ([5f8fbd7](https://github.com/halestudio/hale/commit/5f8fbd70c314c7594308a511185874d342ea6705))
* replace broken or risky cryptographic algorithm ([9903a9f](https://github.com/halestudio/hale/commit/9903a9f9dc3f32ea1154e074f75d30a317c77746))
* Xplan validation erroneously throws warning on missing objects ([23bcc04](https://github.com/halestudio/hale/commit/23bcc04dab77289d7953f7bb862e5cdc420305f8)), closes [#890](https://github.com/halestudio/hale/issues/890)

## [5.2.1](https://github.com/halestudio/hale/compare/v5.2.0...v5.2.1) (2024-07-04)


### Bug Fixes

* added scroll functionality to the CheckboxTableViewer for selecting export types for CSV and Excel ([2e0483b](https://github.com/halestudio/hale/commit/2e0483bca3daef7cb5861ed42148af91ce9c2155))
* implicit narrowing conversion in compound assignment ([bfedff0](https://github.com/halestudio/hale/commit/bfedff036c14ce0132ce70f4e3bcf9d1793ad7b2))
* resolve XML external entity in user-controlled data ([88ff8cc](https://github.com/halestudio/hale/commit/88ff8cc43051fee597599f621bd33320d389beba))
* use correct representation of date and time when exporting XML/GML ([305b7ea](https://github.com/halestudio/hale/commit/305b7ea93292f43f4bf153c8d26c40d1c0b81007))

## [5.2.0](https://github.com/halestudio/hale/compare/v5.1.0...v5.2.0) (2024-06-19)


### Features

* add binding to service provider for use in Groovy script ([990a152](https://github.com/halestudio/hale/commit/990a15297ab0d80d9ee84b7315011bec0a9d3c8b))
* add paramter "resolvedepth" in UI when downloading data via WFS ([693b8b8](https://github.com/halestudio/hale/commit/693b8b8a38782f9686af3a310428515edc3674a6)), closes [#1085](https://github.com/halestudio/hale/issues/1085)
* allow usage of LookupTableImpl and LookupTableInfoImpl in Groovy ([6b96ff9](https://github.com/halestudio/hale/commit/6b96ff9c7c8820aa86c92c75c5d75799085cb261))
* CSV, Excel and JSON writer - reduce to single geometries if data does not contain multi geometry ([3aa38bf](https://github.com/halestudio/hale/commit/3aa38bf95074c90ee4cf070e659a5cece7d11d4c)), closes [#986](https://github.com/halestudio/hale/issues/986)
* update version number to 5.2.0 for the next snapshot ([5fbde49](https://github.com/halestudio/hale/commit/5fbde49e3fd637522d824183df07443608716a99))


### Bug Fixes

*  functions not working for elements with value and attribute ([2493822](https://github.com/halestudio/hale/commit/2493822d4623911dd3b0d45b49d565b9f50efb7f))
* add quotes to automatically generated condition contexts on parent ([72b243d](https://github.com/halestudio/hale/commit/72b243d9ef88a8ff35623ca98e08b750c21e5f09))
* allow filtering for joins ([4309686](https://github.com/halestudio/hale/commit/4309686bfa04797ad6543172155501ffb8a6231e))
* **deps:** update dependency de.undercouch:gradle-download-task to v5 ([9d9a370](https://github.com/halestudio/hale/commit/9d9a370008514482840af73bcdf3cfe68b38d85c))
* **deps:** update dependency org.apache.maven.plugin-tools:maven-plugin-annotations to v3.13.0 ([b8241c1](https://github.com/halestudio/hale/commit/b8241c1b4835e7f38af885db197fd371deb3e8a1))
* **deps:** update dependency org.apache.maven.resolver:maven-resolver-api to v1.9.20 ([d56e083](https://github.com/halestudio/hale/commit/d56e083333fd46f309c9088eee4820de9f44ae54))
* **deps:** update dependency org.apache.maven.wagon:wagon-http to v3.5.3 ([f23c9fa](https://github.com/halestudio/hale/commit/f23c9fa710b23025c08279b9347d2e906f4955db))
* **deps:** update dependency org.eclipse.jgit:org.eclipse.jgit to v6 ([4a6064f](https://github.com/halestudio/hale/commit/4a6064f7bd3e3636ff7ceb6f31e027a114e2960f))
* **deps:** update dependency org.slf4j:slf4j-simple to v2 ([71567f7](https://github.com/halestudio/hale/commit/71567f78caeb2951fe85114a3a787137f4e9ac4c))
* **deps:** update dependency org.yaml:snakeyaml to v2 ([7d321e3](https://github.com/halestudio/hale/commit/7d321e36e4b5c5c08950b77bca02b8a2f7fd53d1))
* **deps:** update dependency to.wetransform.offlineresources.feature to v2024.5.23.bnd-osxq0g ([db6b415](https://github.com/halestudio/hale/commit/db6b415e243ceec677c87105eda26a42a0571cf1))
* **deps:** update dependency to.wetransform.offlineresources.feature to v2024.6.13.bnd-dc5qw ([160ac42](https://github.com/halestudio/hale/commit/160ac42be45356a8d9b7882d92c64625f05908fd))
* **deps:** update dependency to.wetransform.offlineresources.feature to v2024.6.18.bnd-nmxrdq ([ef6534f](https://github.com/halestudio/hale/commit/ef6534f90d202b8a651bf93061f3773e59403b48))
* **deps:** update other org.apache.maven.wagon dependencies ([1e87ef5](https://github.com/halestudio/hale/commit/1e87ef58de6e345cba7ae097214d3286a8bbf1e7))
* don't close PrintStream when using printCell ([ca99edf](https://github.com/halestudio/hale/commit/ca99edf3d6c93339c2dca623aaa9b7a4bf2db7e5))
* fix provider definition for geometry.unifyWindingOrder parameters ([4cca094](https://github.com/halestudio/hale/commit/4cca0949262e3e5c80b7bb031c1ccfbb9b3d578e))
* Reload and update schemas - saves the last  changes ([a80cf5a](https://github.com/halestudio/hale/commit/a80cf5a1936747853a1594b6a0e7a6142960800f)), closes [#956](https://github.com/halestudio/hale/issues/956)
* use appropriate methods to convert the numbers in XML ([2baec50](https://github.com/halestudio/hale/commit/2baec50f5ba9117f09942c13725b031a449c17f0))

## [5.1.0]

### Added
- Support for reading multiple sheets from an Excel file simultaneously
- Enable exporting of multiple feature types to Excel
- Allow loading Excel which contains blank rows and sheets for schema and source data. Instances are not added for blank entries
- Add predefined and custom formatters for Date cells in Excel import
- Enable exporting feature types with no data in Excel
- Align Excel with CSV export wizard
- Prefer properties with short paths as GeoJSON geometry property
- Add wizard page to select the read mode for GeoJSON/JSON
- Support for importing data from JSON files, enhancing the versatility of data import options
- Support extracting from JSON schema, providing users with greater flexibility in handling JSON data structures
- Improve the export to Shapefiles including feature types with no geometry
- Support adding multiple selections of feature types to be exported to CSV
- Replace default map with OpenStreetMap
- Add Boolean and Java Pattern class to Groovy restriction whitelist
- When exporting data as Json or GeoJson, now up to 20 decimal places are retained in Geometry coordinates.
- Extend Join functions with optional behavior as "inner joins" where a result is only created if all join conditions are met
- Update offline versions of INSPIRE schemas to 2024.1.2

## Maintenance
- Update Geotools to version 29.1
- Update dependencies in hale-platform to resolve security issues
- Fix for the Windows installer

### Fixed
- Exclude empty rows from instance creation
- Fix shift of attribute values for empty cells in Shapefile export
- Fix TopoJSON encoding
- Fix endless loop when skipping instances in paginated WFS request
- Fix exporting HSD schemas for schemas with no namespace
- Fix Type selection broken on custom export config
- Fix an issue that prevented the more performant index based Join and Merge implementations from being used
- Fix for index based Merge for cases where no Merge key is used

## [5.0.1]

### Added
- Add an option to overwrite content in Geopackage Writer instead of appending content
- Add a parameter to the GeoPackage writer that allows creating tables for all mapping-relevant target types
- Create a code page (.cpg) file when exporting a Shapefile

### Fixed
- Fix the Commons Text security vulnerability
- Fix to Proxy settings to be able to contact update sites when Proxy usage is required
- Fix the automation of the build process

## [5.0.0]

### Added
- Support for Java 17
- Support for Groovy 2.5
- Support to configure details on HTTP client max connections and connection eviction via environment variables
- Support collecting metrics on Request class HTTP clients
- Support for TopoJSON as a format for exporting data
- Support to get list of Shapefile names
- Support for XPlanGML 5.4 and XPlanGML 6.0 schema preset
- Support to allow excluding root properties from structural Retype
- Support to automatically add the codespace for INSPIRE feature types
- Support for `xs:all` to the XML Schema Reader
- Support to specify initial value during "Generate sequential ID" function
- Support to read CRS information from Geopackage file rather than reading it from the schema
- Enhanced support for loading XML/GML schema resembling Geopackage structure so that no information is lost
- Support to specify leniency when using DateExtraction function
- Support to skip n-lines when reading csv and xls files

### Changed
- Merge schemas for HSD export as XML or Json to one schema to work around the restriction that only single schema can be imported from HSD file

### Removed
- Deegree workspace configuration generation has been removed since it requires further work to integrate updated dependencies that are compatible with Java 17

### Fixed
- Fix to check for a null date when converting a string to any form of date and/or time
- Fix to add exception message to the transformation log
- Fix to propose one single variable or a list during Groovy merge
- Fix to remove group property definitions from path when using Add condition on context function
- Fix to not truncate Shapefile attributes if their length is less than 10 characters
- Fix to correct the winding order as counterclockwise based
  on the right-hand rule when exporting data in Json format
- Fix to preserve original order of columns during CSV export
- Fix to clean progress bar when opening a new project after exporting in CSV and XLS
- Fix to handle NPE when exporting multiple geometries to Shapefiles
- Fix to apply queryFilter when reading Geopackage files instead of writing them
- Fix to allow loading Geopackage files with non standard file extension

## [4.1.0]

### Added
- Support for selecting multiple files during schema and the data import
- END-related schemas added in presets
- Support to remove a single schema from the project view
- Support to export the source and the transformed data to Shapefiles using GeoTools
- Support for missing time zone information when parsing timestamps
- Support for creating a spatial index when writing GeoPackage files
- Support for Java 8 date/time classes in Groovy

### Changed
- Activated XML pretty printing by default in the writer settings dialog page when exporting XML/GML

### Removed
- xtraserver plugin

### Fixed
- Fix application not starting on macOS
- Fix duplicate imports of the same schema when the schema locations differ only in the scheme part
- Fix use of IProxyService to apply proxy configuration in the UI
- Fix validity check of the hale»connect export dialog
- Fix loading of projects in case user context is not supported
- Fix ArcString interpolation handling
- Fix handling of HTTPS schema locations in schema resolver
- Fix for failing PostGIS integration tests
- Fix the last modified check when loading Groovy snippet from file
- Fix failing of CRS without remarks for geopackage
- Fix use of correct preferred bundle when loading geometry types
- Fix to not append time zone information when writing xmlDate

## [4.0.0]

### Added
- Support for reading and writing GeoPackage files
- Improved support for writing XPlanGML files ([#814](https://github.com/halestudio/hale/issues/814))
- XPlanGML schema presets ([#799](https://github.com/halestudio/hale/issues/799))
- Additional AAA NAS schema presets ([#615](https://github.com/halestudio/hale/issues/615))
- TN-ITS schema preset
- ISO 19139 GMD schema preset
- Support for comparing Integer and Float values in a Join ([#737](https://github.com/halestudio/hale/issues/737))
- Support for MongoDB URI scheme when automatically updating paths ([#762](https://github.com/halestudio/hale/issues/762))
- Continuous integration with Travis CI
- Support for loading hale connect projects in headless environment
- Ability to filter spatial index query by type in Groovy scripts
- New options in the deegree configuration exporter to allow inclusion of abstract and feature collection types in mappings
- Groovy wrapper function for parsing JSON data
- Allow use of `java.util.TreeMap` in Groovy scripts

### Changed
- Package OpenJDK binaries instead of Oracle ones ([#758](https://github.com/halestudio/hale/issues/758))
- Upgrade GeoTools dependency to 21.0 ([#821](https://github.com/halestudio/hale/issues/821))
- Upgrade RCP to Eclipse 4.15 (2020-03) ([#822](https://github.com/halestudio/hale/issues/822))
- Limit supported TLS version in hale connect integration to v1.2 and cipher suites to those with perfect forward security

### Removed
- Support for 32-bit builds
- Server product

### Fixed
- Fix possible deadlock when copying XML schemas with complete dependencies
- Prevent possible `NullPointerException` in snippet service in headless environment
- Don't fail spatial index query if an instance can't be resolved
- Don't fail deegree export when the Primitive option `All link where code lists are assigned in hale` is selected but there are no code lists in the alignment
- Fix for GML reader to correctly recognize certain elements ([#764](https://github.com/halestudio/hale/issues/764))
- Fix display problems in hale connect integration ([#752](https://github.com/halestudio/hale/issues/752))
- Fix problem with stale cache in hale connect integration
- Fix problem in headless environment that lead to duplication of instances in the transformation output ([#774](https://github.com/halestudio/hale/issues/774))
- Allow type filtering of `LimboInstanceSink` instance collections
- Fix parsing of XML value lists ([#808](https://github.com/halestudio/hale/issues/808))
- Fix rendering of target geometries with default styling ([#732](https://github.com/halestudio/hale/issues/732))
- Fix parsing of GML dictionaries ([#824](https://github.com/halestudio/hale/issues/824))
- Fix code list import where all XML files were being incorrectly detected as SKOS code lists

## [3.5.0]

### Added
- Deegree feature store configuration export
- GML partitioning by spatial extent
- Support for plugin installation via update sites
- Improved Xtraserver export
- Migration support for join conditions and context filters
- Support for alignments with inline transformations in headless mode
- Added geometry metadata to Property Type section of Properties view
- Summary in transformation reports
- Support for matching shortened Shapefile properties

### Changed
- Upgraded RCP to Eclipse 4.8 (Photon)
- Upgraded Groovy to 2.4
- Upgraded PostgreSQL driver to 42.2.4, PostGIS driver to 2.2.1
- Added JTS Geometry classes to Groovy white list

### Removed

- Moved App-Schema configuration export to external plugin

### Fixed
- Fixed file names when partitioning by feature type in a GML export
- Improved performance for inline transformations where inlined transformation contains Groovy scripts
- Prevent change of project resources paths if project is exported to hale connect
- Prevent removal of existing source data when loading additional source data
- Allow loading hale schema definition file even if contraint type can't be recreated
- Ensure spatial index is always available in CLI transformations
- Fixed listing of hale connect projects
- Fixed application freezes on macOS

## [3.4.1]

### Changed
- Updated GML XSD to version 3.2.2

### Fixed
- Fixed problems with AppSchema configuration dialog on Windows
- Allow loading the same Excel lookup table multiple times
- Support partitioning by feature type when transforming external data
- Support srsDimension attribute also at coordinates level
- Fixed Compatibility Mode toolbar on Windows
- Removed invalid AdV CRS code mappings
- Fixed possible `NullPointerException`s in GML export, lookup tables, transformation
- Added missing configuration page to wizard for Excel lookup tables
- Fixed number and length validators to support value lists
- Prevent transformation run when sampling causes reloading of source data
- Force replacement of `\r` line endings in Groovy scripts

## [3.4.0]

### Added
- Support for isolated workspaces to App-Schema plugin
- XtraServer configuration plugin
- Support for merging alignments and viewing related tasks
- Allow to split GML output by feature type
- Capability to import Groovy snippets and use them in transformation scripts
- Preset for AAA XML schema
- Support for several AdV CRS codes
- Option to ignore the total number of features reported by a WFS
- Option to format non-integer decimals in XML/GML output
- Support for replacing source and target entities of a cell
- CLI option to output transformation statistics
- Capability to define custom transformation success conditions based on statistics
- Support to access the same property on all children in Groovy scripts

### Changed
- Allow ".txt" extension for CSV files
- Preserve annotations, ID and re-use existing functions parameters when replacing a cell
- Retain Join configuration when adding/removing types
- Allow to skip entities in remapping wizard
- Added warning to CRS selection dialog if WKT does not contain Bursa-Wolf parameters

### Fixed
- Fixed hale connect integration when using a proxy
- Fixed hale connect project list and versioning support
- Fixed hale connect integration if user is a member of multiple organisations
- Fixed opening hale connect project with subfolders
- Fixed CLI transformations when source data contains unknown or invalid CRS definitions
- Fixed fallback mechanism in index merge handler
- Fixed Spatialite export to destinations when destination path contains spaces
- Apply proxy settings also to HTTPS connections
- Fixed Spatial Index Groovy helper function
- Fixed that defaultSrs parameter of XML/GML readers had no effect in CLI
- Fixed that transformation runs were being triggered without data and/or multiple times during project load
- Fixed that compression could not be used when partitioning GML output

## [3.3.2]

### Added
- Instance index to improve execution of Merge and Join transformations
- Enhanced CRS detection when parsing GML files
- When importing a shapefile resource, prefill character set dialog with encoding read from accompanying `.cpg` file
- Added support for multiple organisations to hale connect integration
- Support to automatically update Join and Merge properties in case of a schema remapping
- Support for ECQL expressions in filters and condition contexts

### Changed
- Partitioning modes `none`, `cut` and `related` for GML output
- Support for `noNamespaceSchemaLocation` in GML output
- Support for loading XLS files multiple times
- Support relogin to hale connect without having to clear stored credentials
- Limit number of messages per message type in a report
- Groovy scripts: Whitelisted use of `java.sql.Date` as well as classes needed for creating geometry properties
- Updated default hale connect endpoints

### Fixed
- Fixed opening a project file on launch (e.g. via double-clicking from a file explorer)
- Allow removing a previously assigned code list
- Fixed automatic resource path update to also work with URIs w/ a query part
- Fixed hale connect login on Welcome Page to work for user names and passwords w/ special characters
- Fixed the CRS definition lookup when importing shapefiles, allowing for automatic detection of CRS details (Bursa-Wolf parameters)
- Fixed application of Groovy restrictions when loading a project
- Fixed handling of JDBC collection sizes

## [3.3.1]

### Added
- Support for saving changes directly to hale connect
- Support for partitioning GML output to multiple files
- Support for table type `MATERIALIZED VIEW` when importing a PostgreSQL database schema
- Check for remote changes when sharing project to hale connect
- Support for `Double` columns for the CSV reader
- GML reader parameters `ignoreMappingRelevant` and `suppressParsingGeometry`
- Property constraint `CodeListAssociation`
- Type constraint `MappingRelevantIfFeatureType`

### Fixed
- Opening projects that have MS Access database resources
- `IndexOutOfBoundsException` when calling Groovy helper functions
- Do not add `STARTINDEX` parameter to non-paginated WFS `GetFeature` requests
- Loading resources in headless mode from URL when remote server responds with a redirect
- Loading a project in headless mode no longer fails in cases where code lists cannot be imported
- Loading INSPIRE schemas from local resources when online version is not available
- New projects could be saved only as a project archive if the last project loaded was an archive

## [3.3.0]

### Added
- Integration with the online collaboration platform hale connect: log in to hale connect, import shared transformation projects and upload projects.
- Spatial Index for instances with geometries that can be queried via the new Groovy geometry helper "spatialIndexQuery"
- Spatial Join transformation function: join instances based on the spatial relation of their geometry properties
- Groovy geometry helper function "boundaryCovers" that can be used to determine if the boundary of a the geometry covers all points of a line
- Use arbitrary SQL queries as a source schema and data source
- Import/Export hale schema definitions as JSON
- DMG image for macOS installation

### Changed
- Application title is now "hale studio"
- "Load project from templates..." has been removed in favour of hale connect integration
- Cached schema definition is now used always if loading source fails

### Fixed
- Fixed content assistance in RegEx Analysis function
- Fixed resource copying in hale Project Archive writer
- Fixed links on About screen
- Fixed updating a cell when the source or target types are changed to a parent of the original type

## [3.2.0]

### Added
- Added support for several Arc-based GML geometry types to be interpolated when read: Arc, ArcString, Circle, CircleByCenterPoint. The interpolation is based on one of two algorithms that can be selected on import.
- Project Validator that validates exported instances based on validator configuration (e.g. rules or schemas) imported into the project
- New transformation function `Assign collected values` allows the assignment of all values collected by a Groovy transformation function. The new function automatically converts collected values to references if the target property takes references.
- Better usage of available space in Alignment and Mapping views
- Content assistance for project variables in transformation function wizards such as Formatted String, Regex Analysis and Assign
- Request pagination for WFS requests. Users can now choose to activate request pagination for WFS sources.
- IO Provider extensions can now have a configurationContentType to describe the content type of configuration files for this provider
- Total number of imported instances is now shown in progress dialog (if known)
- The `InstanceResolver` interface has been extended to allow resolving multiple references at once. Implementations can use this to optimize resolving of multiple references

### Changed
- The HTML documentation that can be generated for an alignment is now much more performant for large mappings due to lazy loading and rendering
- The Merge function now uses an iterative approach for merging instances which allows for processing more data in a Merge
- Allow using `SimpleDateFormat` and `UUID` classes in groovy scripts by default
- When loading data from CSV files the data is now streamed (similar to XML data sources) and not loaded at once into memory
- When a CSV files has more columns than defined in the schema, this is now a warning, not an error

### Fixed
- Deselecting in a type selector could lead to an exception
- Removed CRS selection dialog and UI dependency from MS SQL plugin
- Schema selection configuration for JDBC driver is optional
- Fixed wrong tooltip in Mapping view
- Fixed error when loading hale schema definitions in respect to schema elements w/ primitive bindings

## [3.1.0]

### Added
- Support reading from and writing to MS SQL databases
- Instance counts are now calculated for condition and index contexts as well
- You can now hide optional properties in the schema explorer
- SKOS format code lists can now be loaded
- Validation based on a Schematron file can now be performed on an encoded XML/GML file
- To ensure topological consistency in respect to interpolated geometries, other geometries may optionally also be moved to the interpolation grid
- GML Encoding: It is now possible to specify a number format for geometry ordinates, e.g. if you want a fixed precision after the decimal point
- During validation in hale also check property values against an assigned code list
- CLI commands can now be marked experimental
- Added extension point for custom validators for the hale pre-encoding validation

### Changed
- GML encoding: Automatic conversion of polygon geometries to line geometries when there are no possibilities to encode a surface has been changed to produce a *LineString* for a *Polygon* and a *MultiLineString* for a *MultiPolygon*
- The contents of XML Alignment files now are sorted where possible, to have a reproducable encoding for the same mapping and a nice diff when used in version control
- The contents of Hale Schema Definition files now are sorted where possible, to have a reproducable encoding for the same schema and a nice diff when used in version control
- When reading GML geometries composite 2D geometries (e.g. CompositeSurface, Surface with multiple patches, CompositeCurve, etc.) are now by default combined to a single geometry if possible

### Removed
- File based databases can no longer be loaded via *From database* - instead use *From file*

### Fixed
- Using a previously as Hale Schema Definition file exported database schema as source schema when loading data from the database now properly supports loading the geometries
- Using the value `unpopulated` for GML *nilReason* attributes does not conform to the GML specification. The proposal to use this value has been changed to `other:unpopulated` to conform with the specification. Also, when encoding GML, `unpopulated` will be replaced by `other:unpopulated` where encountered in *nilReason* attributes to support mappings created in previous versions
- Fixed error in instance partitioning for WFS-T upload when encountering unresolvable references
- Fixed instance partitioning for WFS-T upload producing too many small parts
- Resolving local resources via bundles can no longer yield streams to directories/packages
- Groovy geometry helper functions don't fail for null input
- Exporting project archives for new projects now works as expected
- On export to GeoServer AppSchema via the REST endpoint the provided URL now may end with a slash
- Mapping cells in the mapping view are now ensured to be updated when edited or deleted
- When exporting data to XML files include root element schema in schema location attribute

### Deprecated
- Deprecated old style HTML mapping documentation, instead use the new HTML+SVG mapping documentation
- Deprecated INSPIRE 3.0 specific mapping functions (INSPIRE Identifier and Geographical Name), instead map to sub-properties individually

## [3.0.0]

### Added
- Users can now configure custom maps based on a Tile URL pattern or a Web Map Service
- Generic command line interface with commands that can be added via an extension point, added commands for documentation generation
- Transformation command line interface: Data included as source for the transformation using the now can be filtered, also arguments can now be passed to the command line interface using an arguments file
- MS Access database reader for `.mdb` files based on [UCanAccess](https://sourceforge.net/projects/ucanaccess/)
- Three new priority levels for mapping cells
- `Collector` helper class for easily collecting information or building indexes in Groovy functions, also added a helper function that will create a default collector in a transformation context
- Internal instance validation now includes validation of local XLink references. Local references that cannot be resolved result in a warning
- hale checks the version of a project that is loaded and displays a warning when the project was created with a newer version of hale than the one currently used
- Support for project variables has been added, that allow customizing the behavior of an alignment. The variables can be defined and stored in the project, and overridden for transformation execution
- SHA-256 Groovy helper function
- Several Groovy helper functions for dealing with and creating geometries
- Users can now define custom transformation functions (using Groovy) that are stored in the alignment
- For XML Schema it is now possible to also map types w/o global element definition (option on schema import)
- *Interior Point* transformation function that determines a point that us guaranteed to lie inside a geometry
- Support for 1-dimensional arrays as multi-valued properties for JDBC schema and data sources (only tested with PostgreSQL)
- `XmlSchemaReader` can be configured with specific content for anyType elements (to be able to map them properly; not configurable via the UI)
- WFS-T writers now can be configured for services protected with HTTP Basic Authentication
- New `decimal` parameter added to identify the float value in CSV file with specified separator

### Changed
- New default base map is [Stamen Terrain](http://maps.stamen.com/terrain/#3/42.62/15.29)
- The WFS 2.0 Feature Collection writer can now be configured to skip counting features (allowing direct streaming instead of temporary storing all features)
- Mapping cells are now sorted in a specific order when they are saved, to have easier understandable diffs when using `.halex` projects in version control
- Writing XmlDateTime values when encoding XML now uses a fixed default timezone (UTC) instead of the system time zone
- Type transformations are now executed in order according to their configured priority (this means that for instance a type transformation w/ high priority is guaranteed to be completely executed before a type transformation with normal priority)
- Several improvements to the HTML+SVG mapping documentation
- The winding order of GML geometries is now fixed when encoding a GML file, also the behavior regarding winding order is now configurable
- Groovy restrictions: Access to the `QName` class is now allowed by default
- Process URI and URL values as Strings when comparing keys in a *Join*
- Mapping cell explanations can now be available in multiple languages, by default English and German are supported
- Groovy script input variables are no longer converted to Strings to allow handling instances and other kinds of objects
- An encoding can now be specified when loading a schema from a Shapefile
- hale now requires Java 8
- hale is now based on Eclipse Mars (from Eclipse Luna)
- hale libraries can now be used on a non-OSGi environment (this implies a lot of internal changes regarding service provision and discovery)
- Groovy type transformations now may return multiple results with multiple `_target` closures
- Proxy credentials where the user name includes a backslash (`\`) are interpreted as NTLM credentials with the user name specifying both domain and user
- XML/GML writers will write `nilReason` attributes only if the element is actually nil

### Removed
- The OpenStreetMap based map provided by MapQuest has been removed as the service is [no longer available](http://devblog.mapquest.com/2016/06/15/modernization-of-mapquest-results-in-changes-to-open-tile-access/) (see also #64)

### Fixed
- Errors when converting the alignment model on saving will no longer result in empty files overriding your previously stored alignment
- Time information for dates no longer is lost when stored in the internal database
- Concatenating a Ring geometry to a LinearRing will no longer result in duplicate coordinates
- Concurrent access to the same source instances could result in exceptions and invalid objects being transformed (see #96)
- Drastically reduced threads created by `FinalizableReferenceQueue`s for internal database handle cleanup
- PostGIS: classify columns with type `geometry` as geometry columns, even if there is no corresponding entry in the `geometry_columns` table
- PostGIS: assume lon/lat axis order instead of lat/lon for geographic coordinate reference systems
- Correctly use cursors in JDBC connections (loading tables in batches, not completely)
- Returning muliple instances from Groovy functions now always uses the correct bindings

## 2.9.4 - 2015-11-01

Changes so far have been documented in the [hale help](http://help.halestudio.org/latest/topic/eu.esdihumboldt.hale.doc.user/html/new/2_9_0.xhtml?cp=0_2_13).

[5.1.0]: https://github.com/halestudio/hale/compare/5.0.1...5.1.0
[5.0.1]: https://github.com/halestudio/hale/compare/5.0.0...5.0.1
[5.0.0]: https://github.com/halestudio/hale/compare/4.1.0...5.0.0
[4.1.0]: https://github.com/halestudio/hale/compare/4.0.0...4.1.0
[4.0.0]: https://github.com/halestudio/hale/compare/3.5.0...4.0.0
[3.5.0]: https://github.com/halestudio/hale/compare/3.4.1...3.5.0
[3.4.1]: https://github.com/halestudio/hale/compare/3.4.0...3.4.1
[3.4.0]: https://github.com/halestudio/hale/compare/3.3.2...3.4.0
[3.3.2]: https://github.com/halestudio/hale/compare/3.3.1...3.3.2
[3.3.1]: https://github.com/halestudio/hale/compare/3.3.0...3.3.1
[3.3.0]: https://github.com/halestudio/hale/compare/3.2.0...3.3.0
[3.2.0]: https://github.com/halestudio/hale/compare/3.1.0...3.2.0
[3.1.0]: https://github.com/halestudio/hale/compare/3.0.0...3.1.0
[3.0.0]: https://github.com/halestudio/hale/compare/2.9.4...3.0.0
