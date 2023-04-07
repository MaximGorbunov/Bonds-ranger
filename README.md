# Bonds ranger
CLI для сортировки поиска прибыльных облигаций

---
## Arguments
* --risk LOW,MODERATE - фильтр для поиска облигаций по уровню риска
* --period 30 - ожидаемый период владения облигацией для расчета прибыли
* --currency rub - фильтрация облигаций по валюте
* --ruonia 7.61 - текущая ставка RUONIA для расчета ожидаемой прибавки к ОФЗ
* --token - Tinkoff API token

## How to run
1. ./gradlew shadowJar
2.  java -jar build/libs/BondsRanger-1.0-SNAPSHOT-all.jar --risk LOW --period 30 --inflation 7.5 --currency rub --ruonia 7.61 --token <YOUR_TOKEN>