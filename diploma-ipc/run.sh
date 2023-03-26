#sudo docker-compose up --build
#curl localhost:3030/api/mq/meter

for i in {1..100}; do
  echo "$i"
  curl localhost:3030/api/meter
done
