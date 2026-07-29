-- Design §8 default weights. Destination formula sums to 1.0; activity
-- formula sums to 1.0 (proximityToDayCluster's weight is folded into rating
-- and cost — see ADR on simplified activity scoring — since it needs
-- day-item geodata this service deliberately doesn't own, design §3.1).
insert into scoring_weights (key, value) values
    ('destination.interestMatch', 0.35),
    ('destination.budgetFit',     0.25),
    ('destination.seasonFit',     0.20),
    ('destination.popularity',    0.20),
    ('activity.tagOverlap',       0.40),
    ('activity.rating',           0.30),
    ('activity.costFit',          0.30);
