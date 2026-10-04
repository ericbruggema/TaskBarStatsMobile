package com.ericbruggema.taskbarstats;

// Draait via Shizuku in een proces met shell-rechten (mag /proc/stat lezen).
interface IStatsService {
    // Door Shizuku vereist: transactiecode 16777114
    void destroy() = 16777114;

    // Eerste regel van /proc/stat ("cpu  user nice system idle iowait ...")
    String readProcStat() = 1;

    // Uitvoer van "top -b -n 1" (proceslijst met CPU en geheugen), alleen mogelijk met shell-rechten
    String topProcesses() = 2;

    // Details van een proces ("sleutel<TAB>waarde" per regel): opdrachtregel, ouder, gebruiker, leeftijd, wat het proces draaiende houdt
    String processDetails(int pid) = 3;

    // Beperkte acties met shell-rechten: 1 = am force-stop <pakket>, 2 = kill -9 <pid>, 3 = alle caches wissen (pm trim-caches). Geeft de exitcode.
    int runAction(int kind, String arg) = 4;
}
