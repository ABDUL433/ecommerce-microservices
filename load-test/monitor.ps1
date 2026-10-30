# Samples about every 15 seconds:
#   lag  = messages waiting in Kafka for each consumer group and topic (a growing number = a slow consumer)
#   cpu  = CPU use of each Docker container
#   host = CPU and memory of the whole laptop (this includes the three Spring Boot services)
# Normally started by benchmark.ps1 together with the load test.
param([int]$Samples = 12)

for ($i = 1; $i -le $Samples; $i++) {
    $lag = @{}
    docker exec ecommerce-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups 2>$null | ForEach-Object {
        $f = $_ -split '\s+'
        if ($f.Count -ge 6 -and $f[5] -match '^\d+$') {
            $key = "$($f[0])/$($f[1])"
            $lag[$key] = [int]$lag[$key] + [int]$f[5]
        }
    }
    $cpu = docker stats --no-stream --format "{{.Name}}={{.CPUPerc}}" | Out-String
    $hostCpu = [math]::Round((Get-Counter '\Processor(_Total)\% Processor Time').CounterSamples[0].CookedValue)
    $os = Get-CimInstance Win32_OperatingSystem
    $usedGb = [math]::Round(($os.TotalVisibleMemorySize - $os.FreePhysicalMemory) / 1MB, 1)
    $totalGb = [math]::Round($os.TotalVisibleMemorySize / 1MB, 1)

    "{0}  lag: {1}" -f (Get-Date -Format HH:mm:ss), (($lag.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join "  ")
    "           host: cpu=$hostCpu%  memory=$usedGb/$totalGb GB"
    "           docker cpu: " + (($cpu -split "`r?`n" | Where-Object { $_ }) -join "  ")
    Start-Sleep -Seconds 5
}
