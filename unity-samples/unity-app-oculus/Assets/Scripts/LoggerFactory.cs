using UnityEngine;

public class LoggerFactory
{
    private LoggerFactory() { }

    public static Logger GetLogger(string name)
    {
        return new Logger(name);
    }
}
