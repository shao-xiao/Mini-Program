UPDATE building_room r
INNER JOIN contract c
  ON c.room_id = r.id
  AND c.deleted = 0
  AND c.status IN ('TERMINATED', 'CANCELLED')
LEFT JOIN contract active_contract
  ON active_contract.room_id = r.id
  AND active_contract.deleted = 0
  AND active_contract.status = 'ACTIVE'
SET r.rent_status = 'VACANT',
    r.updated_by = 0
WHERE r.deleted = 0
  AND active_contract.id IS NULL;
