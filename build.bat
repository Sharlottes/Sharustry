@rem put this project path into PATH_FROM
setlocal
set PATH_FROM=C:\Users\user\Documents\GitHub\sharustry
@rem put your mindustry local path into PATH_TO
setlocal
set PATH_TO=C:\Users\user\AppData\Roaming\Mindustry

if exist %PATH_TO%\mods\SharustryDesktop.jar del %PATH_TO%\mods\SharustryDesktop.jar
xcopy %PATH_FROM%\build\libs\SharustryDesktop.jar %PATH_TO%\mods\ /k /y