-- Cost / User / Account
SELECT
  user_owner,
  linked_account_id                                              AS account_id,
  product_name,
  sum(cast(blended_cost AS DOUBLE))                         AS cost,
  cast(date_parse(usage_start_date, '%Y-%m-%d %T') AS DATE) AS start_date
FROM databasename.tablename
WHERE blended_cost != 'BlendedCost'
AND blended_cost != 'blended_cost'
AND blended_cost != '0.0000000000'
AND usage_start_date != ''
AND usage_start_date != 'UsageStartDate'
AND user_owner != 'user:Owner'
AND user_owner != ''
AND CAST (date_parse(usage_start_date, '%Y-%m-%d %T') AS DATE ) > ( CURRENT_DATE - INTERVAL '30' DAY )
GROUP BY user_owner, linked_account_id, product_name, CAST (date_parse(usage_start_date, '%Y-%m-%d %T') AS DATE )
ORDER BY user_owner, linked_account_id, start_date ASC;