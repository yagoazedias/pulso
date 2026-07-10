from pulso.loader.batch import Batcher
from pulso.xml import transform

ACTIVITY_COLUMNS = [
    "date_components",
    "active_energy_burned", "active_energy_burned_goal", "active_energy_burned_unit",
    "apple_move_time", "apple_move_time_goal",
    "apple_exercise_time", "apple_exercise_time_goal",
    "apple_stand_hours", "apple_stand_hours_goal",
]


def make_batcher(pool, batch_size):
    return Batcher(pool, "activity_summary", ACTIVITY_COLUMNS, batch_size)


def process(batcher, element):
    data = transform.activity_summary_element_to_map(element)
    batcher.add((
        data["date_components"],
        data["active_energy_burned"], data["active_energy_burned_goal"], data["active_energy_burned_unit"],
        data["apple_move_time"], data["apple_move_time_goal"],
        data["apple_exercise_time"], data["apple_exercise_time_goal"],
        data["apple_stand_hours"], data["apple_stand_hours_goal"],
    ))
