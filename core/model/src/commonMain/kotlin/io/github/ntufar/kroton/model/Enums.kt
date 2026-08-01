package io.github.ntufar.kroton.model

enum class MuscleGroup {
    CHEST,
    FRONT_DELTS,
    SIDE_DELTS,
    REAR_DELTS,
    LATS,
    UPPER_BACK,
    TRAPS,
    LOWER_BACK,
    BICEPS,
    TRICEPS,
    FOREARMS,
    ABS,
    OBLIQUES,
    QUADS,
    HAMSTRINGS,
    GLUTES,
    CALVES,
    ADDUCTORS,
    ABDUCTORS,
    NECK,
    FULL_BODY,
    CARDIO,
}

enum class ExerciseType {
    WEIGHT_REPS,
    BODYWEIGHT_REPS,
    WEIGHTED_BODYWEIGHT,
    ASSISTED_BODYWEIGHT,
    DURATION,
    DISTANCE_DURATION,
    REPS_ONLY,
    WEIGHT_DISTANCE,
}

enum class Equipment {
    BARBELL,
    DUMBBELL,
    MACHINE,
    CABLE,
    KETTLEBELL,
    BODYWEIGHT,
    BAND,
    PLATE,
    SMITH,
    EZ_BAR,
    TRAP_BAR,
    SLED,
    SUSPENSION,
    OTHER,
}

enum class Force { PUSH, PULL, STATIC }

enum class Mechanic { COMPOUND, ISOLATION }

enum class SetType { NORMAL, WARMUP, DROP, FAILURE, MYOREP }

enum class RecordType {
    MAX_WEIGHT,
    MAX_REPS,
    BEST_1RM,
    BEST_SET_VOLUME,
    BEST_SESSION_VOLUME,
    MAX_DURATION,
    MAX_DISTANCE,
}

enum class OneRmFormula { EPLEY, BRZYCKI, LOMBARDI, OCONNER }

enum class UnitKind { MASS, LENGTH, PERCENT, COUNT }

enum class PhotoPose { FRONT, SIDE, BACK, OTHER }

enum class Sex { MALE, FEMALE }
