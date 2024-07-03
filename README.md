# SABD Project 2

## Deployment

Download dataset into `data/dataset.csv` and configure `.env` based on
`example.env`

Run with:

```bash
./scripts/run.sh <framework> <query> <workers>
```

where:

- `<framework>` can be: `flink` or `spark`
- `<query>` can be: `1` or `2`
- `<workers>`, optional; default 1

## Queries

### Query 1

For the vaults with ID between 1000 e 1020, calculate the number of events,
average value and standard deviation of the temperature measured on its hard
disks (`s194_temperature_celsius`). Calculate the query on the time windows
(event time):

- 1 day
- 3 days
- from the beginning of the dataset

### Query 2

Calculate the updated real-time ranking of the 10 vaults that record the most
number of failures on the same day. For each vault, report the number of
failures, models e serial number of failed hard disks. Calculate the query on
the time windows (event time):

- 1 day
- 3 days
- from the beginning of the dataset

## Development Environment Setup

### Scala

**Flink**:

```bash
cd src/flink
sbt bloopInstall
```

### Python

```bash
cd src/producer
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```
