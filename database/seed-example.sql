-- Run after all five hazard services have started once and created their tables.
-- Fixed IDs make this seed file safe to rerun without duplicate sample incidents.
USE dpdms_flood;
INSERT IGNORE INTO incidents (id,ward,district,province,occurred_at,reporter,severity,status,latitude,longitude,indicators_json,created_at,updated_at)
VALUES (1,'Rushinga Ward 1','Rushinga','Mashonaland Central','2026-09-20 08:00:00','flood.recorder.ward1','HIGH','APPROVED',-16.784,32.305,JSON_OBJECT('peakWaterLevelMetres',4.2,'riverBasin','Mazowe','householdsDisplaced',34,'areaFloodedHectares',18.5,'inundationDurationDays',3),NOW(),NOW());

USE dpdms_drought;
INSERT IGNORE INTO incidents (id,ward,district,province,occurred_at,reporter,severity,status,latitude,longitude,indicators_json,created_at,updated_at)
VALUES (1,'Rushinga Ward 1','Rushinga','Mashonaland Central','2026-09-18 08:00:00','drought.recorder.ward1','HIGH','APPROVED',-16.790,32.310,JSON_OBJECT('rainfallDeficitMm',88.0,'consecutiveDryDays',42,'cropFailurePercentage',35.0,'peopleWaterShortage',180,'livestockMortalityCount',12),NOW(),NOW());

USE dpdms_fire;
INSERT IGNORE INTO incidents (id,ward,district,province,occurred_at,reporter,severity,status,latitude,longitude,indicators_json,created_at,updated_at)
VALUES (1,'Rushinga Ward 2','Rushinga','Mashonaland Central','2026-09-17 14:30:00','fire.recorder.ward1','MEDIUM','APPROVED',-16.760,32.340,JSON_OBJECT('areaBurnedHectares',6.5,'suspectedCause','accidental','casualties',0,'structuresDestroyed',1,'fireStatus','contained'),NOW(),NOW());

USE dpdms_zoonotic;
INSERT IGNORE INTO incidents (id,ward,district,province,occurred_at,reporter,severity,status,latitude,longitude,indicators_json,created_at,updated_at)
VALUES (1,'Rushinga Ward 3','Rushinga','Mashonaland Central','2026-09-16 09:15:00','zoonotic.recorder.ward1','HIGH','APPROVED',-16.730,32.360,JSON_OBJECT('pathogenName','Anthrax','animalSpeciesAffected','Cattle','confirmedHumanCases',0,'confirmedAnimalCases',4,'classification','cluster'),NOW(),NOW());

USE dpdms_mining;
INSERT IGNORE INTO incidents (id,ward,district,province,occurred_at,reporter,severity,status,latitude,longitude,indicators_json,created_at,updated_at)
VALUES (1,'Rushinga Ward 4','Rushinga','Mashonaland Central','2026-09-15 11:00:00','mining.recorder.ward1','CRITICAL','APPROVED',-16.700,32.380,JSON_OBJECT('mineNameAndType','Makwiro Mine (artisanal)','accidentType','collapse','trappedOrInjuredMiners',2,'fatalities',0,'rescueOperationsOngoing',true),NOW(),NOW());
