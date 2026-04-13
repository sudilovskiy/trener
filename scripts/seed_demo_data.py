import argparse
import sqlite3
from datetime import date, datetime, time, timedelta
from pathlib import Path


EXERCISES_BY_DAY = {
    1: [
        ("pull_ups_ui", "reps"),
        ("ab_wheel_ui", "reps"),
        ("wide_push_ups_ui", "reps"),
        ("dumbbell_flyes_ui", "reps"),
        ("spring_expander_ui", "reps"),
    ],
    2: [
        ("reverse_grip_pull_ups_ui", "reps"),
        ("dumbbell_biceps_ui", "reps"),
        ("bench_abs_ui", "time"),
        ("bench_back_extensions_ui", "reps"),
    ],
    3: [
        ("push_ups_ui", "reps"),
        ("dips_ui", "reps"),
        ("dumbbell_triceps_ui", "reps"),
        ("dips_abs_ui", "time"),
        ("neck_ui", "reps"),
    ],
}


BASE_REPS = {
    "pull_ups_ui": [8, 7, 6],
    "ab_wheel_ui": [14, 12, 10],
    "wide_push_ups_ui": [22, 20, 18],
    "dumbbell_flyes_ui": [15, 14, 12],
    "spring_expander_ui": [20, 18, 16],
    "reverse_grip_pull_ups_ui": [7, 6, 5],
    "dumbbell_biceps_ui": [14, 12, 10],
    "bench_back_extensions_ui": [18, 16, 14],
    "push_ups_ui": [24, 22, 20],
    "dips_ui": [12, 10, 9],
    "dumbbell_triceps_ui": [13, 12, 10],
    "neck_ui": [20, 18, 16],
}


BASE_TIME = {
    "bench_abs_ui": [55, 45, 40],
    "dips_abs_ui": [40, 35, 30],
}


WEIGHTS_BY_EXERCISE = {
    "dumbbell_flyes_ui": [10.0, 10.0, 10.0],
    "dumbbell_biceps_ui": [12.0, 12.0, 10.0],
    "dumbbell_triceps_ui": [10.0, 10.0, 8.0],
}


NOTES = {
    "pull_ups_ui": "Последний подход тяжёлый",
    "bench_abs_ui": "Контроль дыхания",
    "dips_ui": "Хорошая амплитуда",
    "dumbbell_biceps_ui": "Без раскачки",
}


def session_datetime(target_date: date, training_day: int) -> datetime:
    start_hour = {1: 19, 2: 20, 3: 18}[training_day]
    return datetime.combine(target_date, time(hour=start_hour, minute=15))


def workout_duration_seconds(day_offset: int, training_day: int) -> int:
    base = {1: 46 * 60, 2: 42 * 60, 3: 44 * 60}[training_day]
    tweak = (day_offset % 3) * 120
    return base + tweak


def body_weight_for_day(day_offset: int) -> float:
    values = [82.4, 82.2, 82.1, 81.9, 81.8, 81.7, 81.6, 81.4, 81.3, 81.2]
    return values[day_offset]


def reps_with_progress(exercise_id: str, set_index: int, session_index: int) -> int:
    return BASE_REPS[exercise_id][set_index] + min(session_index // 3, 2)


def seconds_with_progress(exercise_id: str, set_index: int, session_index: int) -> int:
    return BASE_TIME[exercise_id][set_index] + min(session_index // 3, 2) * 5


def reset_database(cursor: sqlite3.Cursor) -> None:
    cursor.execute("DELETE FROM workout_session_sets")
    cursor.execute("DELETE FROM workout_sessions")
    cursor.execute("DELETE FROM body_weight_history")
    cursor.execute("DELETE FROM sqlite_sequence WHERE name IN ('workout_sessions', 'workout_session_sets')")


def insert_body_weight_history(cursor: sqlite3.Cursor, end_date: date, days: int) -> None:
    for idx in range(days):
        target_date = end_date - timedelta(days=(days - 1 - idx))
        cursor.execute(
            "INSERT INTO body_weight_history(entryDateEpochDay, weightKg) VALUES(?, ?)",
            (target_date.toordinal() - date(1970, 1, 1).toordinal(), body_weight_for_day(idx)),
        )


def insert_workouts(cursor: sqlite3.Cursor, end_date: date, days: int) -> None:
    for idx in range(days):
        target_date = end_date - timedelta(days=(days - 1 - idx))
        training_day = (idx % 3) + 1
        start_dt = session_datetime(target_date, training_day)
        duration_seconds = workout_duration_seconds(idx, training_day)
        start_ms = int(start_dt.timestamp() * 1000)
        end_ms = int((start_dt.timestamp() + duration_seconds) * 1000)

        cursor.execute(
            """
            INSERT INTO workout_sessions(
                trainingDay,
                startTimestampEpochMillis,
                endTimestampEpochMillis,
                durationSeconds
            ) VALUES (?, ?, ?, ?)
            """,
            (training_day, start_ms, end_ms, duration_seconds),
        )
        session_id = cursor.lastrowid

        for exercise_id, input_type in EXERCISES_BY_DAY[training_day]:
            for set_index in range(3):
                reps = None
                additional_value = None
                if input_type == "reps":
                    reps = reps_with_progress(exercise_id, set_index, idx)
                else:
                    additional_value = float(seconds_with_progress(exercise_id, set_index, idx))

                weight_value = None
                if exercise_id in WEIGHTS_BY_EXERCISE:
                    weight_value = WEIGHTS_BY_EXERCISE[exercise_id][set_index]

                note = NOTES.get(exercise_id, "")
                if set_index > 0:
                    note = ""

                cursor.execute(
                    """
                    INSERT INTO workout_session_sets(
                        workoutSessionId,
                        exerciseId,
                        setNumber,
                        reps,
                        weight,
                        additionalValue,
                        flag,
                        note
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        session_id,
                        exercise_id,
                        set_index + 1,
                        reps,
                        weight_value,
                        additional_value,
                        None,
                        note,
                    ),
                )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("database_path")
    parser.add_argument("--days", type=int, default=10)
    parser.add_argument("--end-date", default=str(date.today()))
    args = parser.parse_args()

    database_path = Path(args.database_path)
    end_date = date.fromisoformat(args.end_date)

    connection = sqlite3.connect(database_path)
    try:
        cursor = connection.cursor()
        reset_database(cursor)
        insert_body_weight_history(cursor, end_date, args.days)
        insert_workouts(cursor, end_date, args.days)
        connection.commit()
        cursor.execute("PRAGMA wal_checkpoint(FULL)")
        connection.commit()
    finally:
        connection.close()


if __name__ == "__main__":
    main()
