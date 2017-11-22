-- User / Resource / Started last week
SELECT
  linked_account_id                       AS account_id,
  user_owner,
  product_name,
  resource_id,
  MIN(usage_start_date)             AS start_date,
  sum(cast(blended_cost AS DOUBLE)) AS cost
FROM wsbillingreports.billingreport
  WHERE blended_cost != 'BlendedCost'
AND blended_cost != 'blended_cost'
AND user_owner != ''
AND resource_id NOT IN
(
SELECT DISTINCT resource_id
FROM wsbillingreports.billingreport
WHERE usage_start_date != ''
AND usage_start_date != 'UsageStartDate'
AND usage_start_date != 'usage_start_date'
AND CAST(date_parse(usage_start_date, '%Y-%m-%d %T') AS DATE) < (CURRENT_DATE - INTERVAL '7' DAY)
)
GROUP BY linked_account_id, user_owner, product_name, resource_id
ORDER BY linked_account_id, user_owner, product_name, resource_id ASC;