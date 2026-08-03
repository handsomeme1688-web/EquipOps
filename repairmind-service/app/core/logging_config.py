import json
import logging



class JsonFormatter(logging.Formatter):
    def format(self, record):
        payload = {
            "timestamp":self.formatTime(record,self.datefmt),
            "level":record.levelname,
            "logger":record.name,
            "message":record.getMessage(),
            "event":getattr(record,"event",None),
            "request_id":getattr(record,"request_id",None),
            "model":getattr(record,"model",None),
            "status":getattr(record,"status",None),
            "duration_ms":getattr(record,"duration_ms",None),
            "token_usage":getattr(record,"token_usage",None),
        }
        return json.dumps(payload, ensure_ascii=False)


def configure_logging(level:str="INFO",logger_name:str="app"):
    logger=logging.getLogger(logger_name)
    logger.setLevel(getattr(logging,level.upper(),logging.INFO))
    if logger.handlers:
        return
    console_handler = logging.StreamHandler()
    formatter = JsonFormatter()
    console_handler.setFormatter(formatter)
    logger.addHandler(console_handler)


